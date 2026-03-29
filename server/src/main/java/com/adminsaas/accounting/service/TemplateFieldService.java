package com.adminsaas.accounting.service;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TemplateFieldService {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");

    private final FileStorageService fileStorageService;

    public TemplateFieldService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public TemplateFieldAnalysis analyze(Long projectId, String relativePath) throws IOException {
        FileStorageService.StoredProjectFile storedFile = fileStorageService.get(projectId, relativePath);
        String originalFilename = storedFile.originalFilename();
        String lowerCaseName = originalFilename.toLowerCase();

        if (lowerCaseName.endsWith(".docx")) {
            return analyzeDocx(Path.of(storedFile.absolutePath()), originalFilename);
        }
        if (lowerCaseName.endsWith(".doc")) {
            return new TemplateFieldAnalysis(originalFilename, "DOC", false, List.of(),
                    List.of("`.doc` 템플릿 자동 분석은 지원하지 않습니다. `.docx`로 변환해 업로드하세요."));
        }
        if (lowerCaseName.endsWith(".hwp") || lowerCaseName.endsWith(".hwpx")) {
            return new TemplateFieldAnalysis(originalFilename, "HWP", false, List.of(),
                    List.of("한글 템플릿 자동 분석은 아직 지원하지 않습니다. `.docx` 템플릿을 권장합니다."));
        }

        return new TemplateFieldAnalysis(originalFilename, "UNKNOWN", false, List.of(),
                List.of("지원하지 않는 템플릿 형식입니다. `.docx` 템플릿을 사용하세요."));
    }

    private TemplateFieldAnalysis analyzeDocx(Path path, String originalFilename) throws IOException {
        Set<String> placeholders = new LinkedHashSet<>();
        try (InputStream inputStream = Files.newInputStream(path); XWPFDocument document = new XWPFDocument(inputStream)) {
            collectPlaceholders(document.getBodyElements(), placeholders);
            for (XWPFHeader header : document.getHeaderList()) {
                collectPlaceholders(header.getBodyElements(), placeholders);
            }
            for (XWPFFooter footer : document.getFooterList()) {
                collectPlaceholders(footer.getBodyElements(), placeholders);
            }
        }

        return new TemplateFieldAnalysis(originalFilename, "DOCX", true, placeholders.stream().toList(), List.of());
    }

    private void collectPlaceholders(List<IBodyElement> bodyElements, Set<String> placeholders) {
        for (IBodyElement element : bodyElements) {
            if (element instanceof XWPFParagraph paragraph) {
                extractPlaceholders(paragraph.getText(), placeholders);
            } else if (element instanceof XWPFTable table) {
                table.getRows().forEach(row ->
                        row.getTableCells().forEach(cell -> collectPlaceholders(cell.getBodyElements(), placeholders)));
            }
        }
    }

    private void extractPlaceholders(String text, Set<String> placeholders) {
        if (text == null || text.isBlank()) {
            return;
        }
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(text);
        while (matcher.find()) {
            placeholders.add(matcher.group(1));
        }
    }

    public record TemplateFieldAnalysis(
            String originalFilename,
            String templateType,
            boolean supported,
            List<String> placeholders,
            List<String> warnings
    ) {
    }
}
