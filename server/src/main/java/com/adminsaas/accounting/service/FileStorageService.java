package com.adminsaas.accounting.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Comparator;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

@Service
public class FileStorageService {

    private final Path uploadRoot;

    public FileStorageService(@Value("${app.storage.upload-dir}") String uploadDir) throws IOException {
        this.uploadRoot = Paths.get(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadRoot);
    }

    public StoredFile store(Long projectId, String category, MultipartFile file) throws IOException {
        String originalFilename = StringUtils.cleanPath(file.getOriginalFilename() == null ? "upload.bin" : file.getOriginalFilename());
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String storedFilename = projectId + "-" + category + "-" + timestamp + "-" + originalFilename;

        Path targetDirectory = uploadRoot.resolve(String.valueOf(projectId)).resolve(category).normalize();
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
            throw new IOException("File not found");
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
            throw new IllegalArgumentException("Invalid file path");
        }
        return resolved;
    }

    private String extractOriginalFilename(String storedFilename) {
        String[] parts = storedFilename.split("-", 4);
        return parts.length == 4 ? parts[3] : storedFilename;
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
