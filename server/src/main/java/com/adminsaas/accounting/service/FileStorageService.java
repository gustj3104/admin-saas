package com.adminsaas.accounting.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.InvalidPathException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
public class FileStorageService {

    private static final int MAX_STORED_FILENAME_LENGTH = 120;
    private static final List<String> WINDOWS_RESERVED_NAMES = List.of(
            "CON", "PRN", "AUX", "NUL",
            "COM1", "COM2", "COM3", "COM4", "COM5", "COM6", "COM7", "COM8", "COM9",
            "LPT1", "LPT2", "LPT3", "LPT4", "LPT5", "LPT6", "LPT7", "LPT8", "LPT9"
    );

    private final Path uploadRoot;

    public FileStorageService(@Value("${app.storage.upload-dir}") String uploadDir) throws IOException {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadRoot);
    }

    public StoredFile store(Long projectId, String category, MultipartFile file) throws IOException {
        String safeCategory = sanitizeCategory(category);
        String originalFilename = sanitizeOriginalFilename(file.getOriginalFilename());
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String storedFilename = projectId + "-" + safeCategory + "-" + timestamp + "-" + originalFilename;

        Path targetDirectory = uploadRoot.resolve(String.valueOf(projectId)).resolve(safeCategory).normalize();
        Files.createDirectories(targetDirectory);
        Path targetPath = targetDirectory.resolve(storedFilename).normalize();

        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, targetPath, StandardCopyOption.REPLACE_EXISTING);
        }

        return new StoredFile(originalFilename, storedFilename, file.getSize(), targetPath.toString());
    }

    public List<StoredProjectFile> list(Long projectId, String category) throws IOException {
        Path projectRoot = uploadRoot.resolve(String.valueOf(projectId)).normalize();
        if (!Files.exists(projectRoot)) {
            return List.of();
        }

        List<StoredProjectFile> files = new ArrayList<>();
        if (category != null && !category.isBlank()) {
            collectFiles(projectId, projectRoot.resolve(category).normalize(), files);
        } else {
            try (DirectoryStream<Path> categories = Files.newDirectoryStream(projectRoot)) {
                for (Path categoryPath : categories) {
                    collectFiles(projectId, categoryPath, files);
                }
            }
        }

        files.sort(Comparator.comparing(StoredProjectFile::uploadedAt).reversed());
        return files;
    }

    public StoredProjectFile get(Long projectId, String relativePath) throws IOException {
        Path resolved = resolveProjectPath(projectId, relativePath);
        if (!Files.exists(resolved) || Files.isDirectory(resolved)) {
            throw new IOException("파일을 찾을 수 없습니다.");
        }

        return toStoredProjectFile(projectId, resolved);
    }

    public ResourceFile load(Long projectId, String relativePath) throws IOException {
        StoredProjectFile storedProjectFile = get(projectId, relativePath);
        byte[] content = Files.readAllBytes(Path.of(storedProjectFile.absolutePath()));
        return new ResourceFile(
                storedProjectFile.originalFilename(),
                Files.probeContentType(Path.of(storedProjectFile.absolutePath())),
                content
        );
    }

    public void delete(Long projectId, String relativePath) throws IOException {
        Path resolved = resolveProjectPath(projectId, relativePath);
        if (Files.exists(resolved) && !Files.isDirectory(resolved)) {
            Files.delete(resolved);
        }
    }

    public void deleteProjectDirectory(Long projectId) throws IOException {
        Path projectRoot = uploadRoot.resolve(String.valueOf(projectId)).normalize();
        if (!Files.exists(projectRoot)) {
            return;
        }

        try (var paths = Files.walk(projectRoot)) {
            paths.sorted(Comparator.reverseOrder()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException exception) {
                    throw new RuntimeException(exception);
                }
            });
        } catch (RuntimeException exception) {
            if (exception.getCause() instanceof IOException ioException) {
                throw ioException;
            }
            throw exception;
        }
    }

    private void collectFiles(Long projectId, Path directory, List<StoredProjectFile> files) throws IOException {
        if (!Files.exists(directory) || !Files.isDirectory(directory)) {
            return;
        }

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(directory)) {
            for (Path file : stream) {
                if (Files.isRegularFile(file)) {
                    files.add(toStoredProjectFile(projectId, file));
                }
            }
        }
    }

    private StoredProjectFile toStoredProjectFile(Long projectId, Path absolutePath) throws IOException {
        Path projectRoot = uploadRoot.resolve(String.valueOf(projectId)).normalize();
        Path relative = projectRoot.relativize(absolutePath.normalize());
        String category = relative.getNameCount() > 1 ? relative.getName(0).toString() : "";
        String storedFilename = absolutePath.getFileName().toString();
        String originalFilename = extractOriginalFilename(storedFilename);
        return new StoredProjectFile(
                relative.toString().replace("\\", "/"),
                category,
                originalFilename,
                storedFilename,
                Files.size(absolutePath),
                Files.getLastModifiedTime(absolutePath).toInstant().toString(),
                absolutePath.toString()
        );
    }

    private Path resolveProjectPath(Long projectId, String relativePath) {
        Path projectRoot = uploadRoot.resolve(String.valueOf(projectId)).normalize();
        Path resolved = projectRoot.resolve(relativePath).normalize();
        if (!resolved.startsWith(projectRoot)) {
            throw new IllegalArgumentException("잘못된 파일 경로입니다.");
        }
        return resolved;
    }

    private String extractOriginalFilename(String storedFilename) {
        String[] parts = storedFilename.split("-", 4);
        return parts.length == 4 ? parts[3] : storedFilename;
    }

    private String sanitizeCategory(String category) {
        String cleaned = StringUtils.cleanPath(category == null ? "" : category).trim();
        if (!StringUtils.hasText(cleaned) || cleaned.contains("..") || cleaned.contains("/") || cleaned.contains("\\")) {
            throw new IllegalArgumentException("Invalid upload category");
        }
        return cleaned;
    }

    private String sanitizeOriginalFilename(String originalFilename) {
        String candidate = StringUtils.cleanPath(originalFilename == null ? "" : originalFilename).trim();
        if (!StringUtils.hasText(candidate)) {
            return "upload.bin";
        }

        String normalized = candidate.replace('\\', '/');
        int separatorIndex = normalized.lastIndexOf('/');
        if (separatorIndex >= 0) {
            normalized = normalized.substring(separatorIndex + 1);
        }

        String sanitized = normalized
                .replaceAll("[\\\\/:*?\"<>|]", "_")
                .replaceAll("\\p{Cntrl}", "_")
                .trim();

        if (!StringUtils.hasText(sanitized) || ".".equals(sanitized) || "..".equals(sanitized)) {
            return "upload.bin";
        }

        sanitized = avoidWindowsReservedName(sanitized);
        sanitized = truncateFilename(sanitized, MAX_STORED_FILENAME_LENGTH);

        try {
            Path.of(sanitized);
        } catch (InvalidPathException exception) {
            return fallbackFilename(sanitized);
        }

        return sanitized;
    }

    private String fallbackFilename(String filename) {
        String lowerCase = filename.toLowerCase(Locale.ROOT);
        if (lowerCase.endsWith(".docx")) return "upload.docx";
        if (lowerCase.endsWith(".doc")) return "upload.doc";
        if (lowerCase.endsWith(".hwpx")) return "upload.hwpx";
        if (lowerCase.endsWith(".hwp")) return "upload.hwp";
        if (lowerCase.endsWith(".pdf")) return "upload.pdf";
        return "upload.bin";
    }

    private String avoidWindowsReservedName(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        String baseName = dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
        String extension = dotIndex > 0 ? filename.substring(dotIndex) : "";
        if (WINDOWS_RESERVED_NAMES.contains(baseName.toUpperCase(Locale.ROOT))) {
            return "_" + baseName + extension;
        }
        return filename;
    }

    private String truncateFilename(String filename, int maxLength) {
        if (filename.length() <= maxLength) {
            return filename;
        }

        int dotIndex = filename.lastIndexOf('.');
        String extension = dotIndex > 0 ? filename.substring(dotIndex) : "";
        String baseName = dotIndex > 0 ? filename.substring(0, dotIndex) : filename;
        int maxBaseLength = Math.max(1, maxLength - extension.length());
        if (baseName.length() > maxBaseLength) {
            baseName = baseName.substring(0, maxBaseLength);
        }
        return baseName + extension;
    }

    public record StoredFile(
            String originalFilename,
            String storedFilename,
            long size,
            String path
    ) {
    }

    public record StoredProjectFile(
            String relativePath,
            String category,
            String originalFilename,
            String storedFilename,
            long size,
            String uploadedAt,
            String absolutePath
    ) {
    }

    public record ResourceFile(
            String filename,
            String contentType,
            byte[] content
    ) {
    }
}
