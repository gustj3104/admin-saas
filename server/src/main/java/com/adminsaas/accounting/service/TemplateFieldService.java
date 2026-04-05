package com.adminsaas.accounting.service;

import com.adminsaas.accounting.template.domain.NormalizedLabel;
import com.adminsaas.accounting.template.domain.NormalizedTable;
import com.adminsaas.accounting.template.domain.NormalizedTemplateContent;
import com.adminsaas.accounting.template.service.StubInspectionReportTemplateParser;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class TemplateFieldService {

    private static final String LABEL_TITLE = "\uAC74\uBA85";
    private static final String LABEL_DATE = "\uACB0\uC81C\uC77C\uC790";
    private static final String LABEL_WRITTEN_DATE = "\uC791\uC131\uC77C";
    private static final String LABEL_EXPENSE_DATE = "\uC9C0\uCD9C\uC77C\uC790";
    private static final String LABEL_VENDOR = "\uB0A9\uD488\uCC98";
    private static final String LABEL_COUNTERPARTY = "\uAC70\uB798\uCC98";
    private static final String LABEL_INSPECTOR = "\uAC80\uC218\uC790";
    private static final String LABEL_CONTRACT_AMOUNT = "\uACC4\uC57D\uAE08\uC561";
    private static final String LABEL_AMOUNT = "\uAE08\uC561";
    private static final String COLUMN_NAME = "\uD488\uBA85";
    private static final String COLUMN_SPEC = "\uADDC\uACA9";
    private static final String COLUMN_QUANTITY = "\uC218\uB7C9";
    private static final String COLUMN_UNIT = "\uB2E8\uC704";
    private static final String COLUMN_UNIT_PRICE = "\uB2E8\uAC00";
    private static final String COLUMN_AMOUNT = "\uAE08\uC561";

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{\\s*([a-zA-Z0-9_.-]+)\\s*}}");
    private static final Map<String, List<String>> TOP_LEVEL_FIELD_ALIASES = new LinkedHashMap<>();
    private static final Map<String, List<String>> TABLE_FIELD_ALIASES = new LinkedHashMap<>();

    static {
        TOP_LEVEL_FIELD_ALIASES.put("title", List.of(LABEL_TITLE));
        TOP_LEVEL_FIELD_ALIASES.put("date", List.of(LABEL_DATE, LABEL_WRITTEN_DATE, LABEL_EXPENSE_DATE));
        TOP_LEVEL_FIELD_ALIASES.put("vendor", List.of(LABEL_VENDOR, LABEL_COUNTERPARTY));
        TOP_LEVEL_FIELD_ALIASES.put("inspector", List.of(LABEL_INSPECTOR));
        TOP_LEVEL_FIELD_ALIASES.put("contractAmount", List.of(LABEL_CONTRACT_AMOUNT, LABEL_AMOUNT));

        TABLE_FIELD_ALIASES.put("itemName", List.of(COLUMN_NAME));
        TABLE_FIELD_ALIASES.put("spec", List.of(COLUMN_SPEC));
        TABLE_FIELD_ALIASES.put("quantity", List.of(COLUMN_QUANTITY));
        TABLE_FIELD_ALIASES.put("unit", List.of(COLUMN_UNIT));
        TABLE_FIELD_ALIASES.put("unitPrice", List.of(COLUMN_UNIT_PRICE));
        TABLE_FIELD_ALIASES.put("amount", List.of(COLUMN_AMOUNT));
    }

    private final FileStorageService fileStorageService;
    private final StubInspectionReportTemplateParser templateParser;

    public TemplateFieldService(FileStorageService fileStorageService, StubInspectionReportTemplateParser templateParser) {
        this.fileStorageService = fileStorageService;
        this.templateParser = templateParser;
    }

    public TemplateFieldAnalysis analyze(Long projectId, String relativePath) throws IOException {
        FileStorageService.StoredProjectFile storedFile = fileStorageService.get(projectId, relativePath);
        String originalFilename = storedFile.originalFilename();
        String lowerCaseName = originalFilename.toLowerCase(Locale.ROOT);

        if (lowerCaseName.endsWith(".docx")) {
            return analyzeDocx(Path.of(storedFile.absolutePath()), originalFilename);
        }
        if (lowerCaseName.endsWith(".hwp") || lowerCaseName.endsWith(".hwpx")) {
            return analyzeStructuredTemplate(projectId, relativePath, originalFilename, lowerCaseName.endsWith(".hwpx") ? "HWPX" : "HWP");
        }
        if (lowerCaseName.endsWith(".doc")) {
            return analyzeStructuredTemplate(projectId, relativePath, originalFilename, "DOC");
        }

        return new TemplateFieldAnalysis(
                originalFilename,
                "UNKNOWN",
                false,
                List.of(),
                List.of("지원하지 않는 템플릿 형식입니다. .docx, .hwp, .hwpx 파일을 사용해 주세요.")
        );
    }

    private TemplateFieldAnalysis analyzeDocx(Path path, String originalFilename) throws IOException {
        Set<String> placeholders = new LinkedHashSet<>();
        StringBuilder plainText = new StringBuilder();
        try (InputStream inputStream = Files.newInputStream(path); XWPFDocument document = new XWPFDocument(inputStream)) {
            collectPlaceholders(document.getBodyElements(), placeholders, plainText);
            for (XWPFHeader header : document.getHeaderList()) {
                collectPlaceholders(header.getBodyElements(), placeholders, plainText);
            }
            for (XWPFFooter footer : document.getFooterList()) {
                collectPlaceholders(footer.getBodyElements(), placeholders, plainText);
            }
        }

        if (placeholders.isEmpty()) {
            placeholders.addAll(detectInspectionReportFields(plainText.toString(), List.of()));
        }

        return new TemplateFieldAnalysis(
                originalFilename,
                "DOCX",
                true,
                placeholders.stream().toList(),
                placeholders.isEmpty()
                        ? List.of("플레이스홀더 또는 물품검수조서 라벨을 찾지 못했습니다.")
                        : List.of()
        );
    }

    private TemplateFieldAnalysis analyzeStructuredTemplate(Long projectId, String relativePath, String originalFilename, String templateType) throws IOException {
        FileStorageService.ResourceFile file = fileStorageService.load(projectId, relativePath);
        InMemoryMultipartFile multipartFile = new InMemoryMultipartFile(
                originalFilename,
                file.contentType() == null ? "application/octet-stream" : file.contentType(),
                file.content()
        );

        NormalizedTemplateContent structuredContent = templateParser.parse(multipartFile);
        List<String> detectedFields = detectInspectionReportFields(structuredContent);

        return new TemplateFieldAnalysis(
                originalFilename,
                templateType,
                true,
                detectedFields,
                detectedFields.isEmpty()
                        ? List.of("문서에서 자동 매핑 가능한 필드를 찾지 못했습니다.")
                        : List.of()
        );
    }

    private void collectPlaceholders(List<IBodyElement> bodyElements, Set<String> placeholders, StringBuilder plainText) {
        for (IBodyElement element : bodyElements) {
            if (element instanceof XWPFParagraph paragraph) {
                String text = paragraph.getText();
                extractPlaceholders(text, placeholders);
                appendPlainText(plainText, text);
            } else if (element instanceof XWPFTable table) {
                table.getRows().forEach(row ->
                        row.getTableCells().forEach(cell -> collectPlaceholders(cell.getBodyElements(), placeholders, plainText)));
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

    private List<String> detectInspectionReportFields(NormalizedTemplateContent structuredContent) {
        List<String> textSources = new ArrayList<>();
        textSources.addAll(structuredContent.labels().stream().map(NormalizedLabel::label).toList());
        textSources.addAll(
                structuredContent.tables().stream()
                        .map(NormalizedTable::headers)
                        .flatMap(List::stream)
                        .toList()
        );
        return detectInspectionReportFields(String.join("\n", textSources), structuredContent.tables());
    }

    private List<String> detectInspectionReportFields(String text, List<NormalizedTable> tables) {
        String normalized = normalize(text);
        Set<String> detected = new LinkedHashSet<>();

        TOP_LEVEL_FIELD_ALIASES.forEach((field, aliases) -> {
            boolean matched = aliases.stream().map(this::normalize).anyMatch(normalized::contains);
            if (matched) {
                detected.add(field);
            }
        });

        tables.stream()
                .map(NormalizedTable::headers)
                .flatMap(List::stream)
                .forEach(header -> TABLE_FIELD_ALIASES.forEach((field, aliases) -> {
                    boolean matched = aliases.stream().map(this::normalize).anyMatch(alias -> alias.equals(normalize(header)));
                    if (matched) {
                        detected.add(field);
                    }
                }));

        return detected.stream().toList();
    }

    private void appendPlainText(StringBuilder plainText, String text) {
        if (text == null || text.isBlank()) {
            return;
        }

        if (plainText.length() > 0) {
            plainText.append('\n');
        }
        plainText.append(text);
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    public record TemplateFieldAnalysis(
            String originalFilename,
            String templateType,
            boolean supported,
            List<String> placeholders,
            List<String> warnings
    ) {
    }

    private static final class InMemoryMultipartFile implements org.springframework.web.multipart.MultipartFile {

        private final String originalFilename;
        private final String contentType;
        private final byte[] content;

        private InMemoryMultipartFile(String originalFilename, String contentType, byte[] content) {
            this.originalFilename = originalFilename;
            this.contentType = contentType;
            this.content = content;
        }

        @Override
        public String getName() {
            return originalFilename;
        }

        @Override
        public String getOriginalFilename() {
            return originalFilename;
        }

        @Override
        public String getContentType() {
            return contentType;
        }

        @Override
        public boolean isEmpty() {
            return content.length == 0;
        }

        @Override
        public long getSize() {
            return content.length;
        }

        @Override
        public byte[] getBytes() {
            return content;
        }

        @Override
        public InputStream getInputStream() {
            return new java.io.ByteArrayInputStream(content);
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            Files.write(dest.toPath(), content);
        }
    }
}
