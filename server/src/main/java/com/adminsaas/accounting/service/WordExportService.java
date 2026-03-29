package com.adminsaas.accounting.service;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class WordExportService {

    private final FileStorageService fileStorageService;

    public WordExportService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public byte[] createEvidenceDocument(EvidenceExportPayload payload) throws IOException {
        Path templatePath = findEvidenceTemplate(payload.projectId());
        if (templatePath != null) {
            return createEvidenceDocumentFromTemplate(templatePath, payload);
        }

        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            addTitle(document, "지출 증빙 문서");
            addParagraph(document, "프로젝트", payload.projectName());
            addParagraph(document, "지출일", payload.paymentDate());
            addParagraph(document, "거래처", payload.vendor());
            addParagraph(document, "품목", payload.itemName());
            addParagraph(document, "카테고리", payload.category());
            addParagraph(document, "하위 카테고리", payload.subcategory());
            addParagraph(document, "지불 방법", payload.paymentMethod());
            addParagraph(document, "금액", payload.amount());
            addParagraph(document, "비고", payload.notes());

            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    public byte[] createSettlementDocument(SettlementExportPayload payload) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            addTitle(document, payload.reportTitle());
            addParagraph(document, "프로젝트", payload.projectName());
            addParagraph(document, "보고일", payload.reportDate());
            addParagraph(document, "사업 기간", payload.projectPeriod());
            addParagraph(document, "작성자", payload.preparedBy());
            addParagraph(document, "확인자", payload.approvedBy());
            addParagraph(document, "요약 메모", payload.summaryNotes());

            XWPFParagraph summaryHeader = document.createParagraph();
            XWPFRun summaryHeaderRun = summaryHeader.createRun();
            summaryHeaderRun.setBold(true);
            summaryHeaderRun.setFontSize(13);
            summaryHeaderRun.setText("카테고리별 정산 집계");

            XWPFTable table = document.createTable(Math.max(payload.items().size(), 1) + 1, 5);
            fillHeader(table.getRow(0), "카테고리", "배정액", "지출액", "차액", "집행률");
            for (int index = 0; index < payload.items().size(); index++) {
                SettlementExportItem item = payload.items().get(index);
                fillRow(table.getRow(index + 1),
                        item.category(),
                        item.allocated(),
                        item.spent(),
                        item.variance(),
                        item.executionRate());
            }

            addParagraph(document, "총 배정액", payload.totalAllocated());
            addParagraph(document, "총 지출액", payload.totalSpent());
            addParagraph(document, "총 차액", payload.totalVariance());
            addParagraph(document, "전체 집행률", payload.executionRate());

            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] createEvidenceDocumentFromTemplate(Path templatePath, EvidenceExportPayload payload) throws IOException {
        try (InputStream inputStream = Files.newInputStream(templatePath);
             XWPFDocument document = new XWPFDocument(inputStream);
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            Map<String, String> placeholders = buildEvidencePlaceholders(payload);
            replaceBodyElements(document.getBodyElements(), placeholders);
            for (XWPFHeader header : document.getHeaderList()) {
                replaceBodyElements(header.getBodyElements(), placeholders);
            }
            for (XWPFFooter footer : document.getFooterList()) {
                replaceBodyElements(footer.getBodyElements(), placeholders);
            }
            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private Path findEvidenceTemplate(Long projectId) throws IOException {
        if (projectId == null) {
            return null;
        }

        return fileStorageService.list(projectId, "evidence-template").stream()
                .filter(file -> file.originalFilename().toLowerCase().endsWith(".docx"))
                .findFirst()
                .map(file -> Path.of(file.absolutePath()))
                .orElse(null);
    }

    private Map<String, String> buildEvidencePlaceholders(EvidenceExportPayload payload) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("projectName", orDash(payload.projectName()));
        placeholders.put("date", orDash(payload.paymentDate()));
        placeholders.put("paymentDate", orDash(payload.paymentDate()));
        placeholders.put("vendor", orDash(payload.vendor()));
        placeholders.put("itemName", orDash(payload.itemName()));
        placeholders.put("category", orDash(payload.category()));
        placeholders.put("subcategory", orDash(payload.subcategory()));
        placeholders.put("paymentMethod", orDash(payload.paymentMethod()));
        placeholders.put("amount", orDash(payload.amount()));
        placeholders.put("notes", orDash(payload.notes()));
        return placeholders;
    }

    private void replaceBodyElements(List<IBodyElement> bodyElements, Map<String, String> placeholders) {
        for (IBodyElement element : bodyElements) {
            if (element instanceof XWPFParagraph paragraph) {
                replaceParagraph(paragraph, placeholders);
            } else if (element instanceof XWPFTable table) {
                table.getRows().forEach(row ->
                        row.getTableCells().forEach(cell -> replaceBodyElements(cell.getBodyElements(), placeholders)));
            }
        }
    }

    private void replaceParagraph(XWPFParagraph paragraph, Map<String, String> placeholders) {
        String originalText = paragraph.getText();
        if (originalText == null || originalText.isBlank()) {
            return;
        }

        String replacedText = originalText;
        for (Map.Entry<String, String> entry : placeholders.entrySet()) {
            replacedText = replacedText.replace("{{ " + entry.getKey() + " }}", entry.getValue());
            replacedText = replacedText.replace("{{" + entry.getKey() + "}}", entry.getValue());
        }

        if (replacedText.equals(originalText)) {
            return;
        }

        while (paragraph.getRuns().size() > 0) {
            paragraph.removeRun(0);
        }

        XWPFRun run = paragraph.createRun();
        run.setText(replacedText);
    }

    private void addTitle(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.setAlignment(ParagraphAlignment.CENTER);
        XWPFRun run = paragraph.createRun();
        run.setBold(true);
        run.setFontSize(18);
        run.setText(text == null || text.isBlank() ? "문서" : text);
    }

    private void addParagraph(XWPFDocument document, String label, String value) {
        XWPFParagraph paragraph = document.createParagraph();
        XWPFRun labelRun = paragraph.createRun();
        labelRun.setBold(true);
        labelRun.setText(label + ": ");

        XWPFRun valueRun = paragraph.createRun();
        valueRun.setText(orDash(value));
    }

    private void fillHeader(XWPFTableRow row, String c1, String c2, String c3, String c4, String c5) {
        row.getCell(0).setText(c1);
        row.getCell(1).setText(c2);
        row.getCell(2).setText(c3);
        row.getCell(3).setText(c4);
        row.getCell(4).setText(c5);
    }

    private void fillRow(XWPFTableRow row, String c1, String c2, String c3, String c4, String c5) {
        row.getCell(0).setText(orDash(c1));
        row.getCell(1).setText(orDash(c2));
        row.getCell(2).setText(orDash(c3));
        row.getCell(3).setText(orDash(c4));
        row.getCell(4).setText(orDash(c5));
    }

    private String orDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    public record EvidenceExportPayload(
            Long projectId,
            String projectName,
            String paymentDate,
            String vendor,
            String itemName,
            String category,
            String subcategory,
            String paymentMethod,
            String amount,
            String notes
    ) {
    }

    public record SettlementExportPayload(
            String reportTitle,
            String projectName,
            String reportDate,
            String projectPeriod,
            String preparedBy,
            String approvedBy,
            String summaryNotes,
            String totalAllocated,
            String totalSpent,
            String totalVariance,
            String executionRate,
            List<SettlementExportItem> items
    ) {
    }

    public record SettlementExportItem(
            String category,
            String allocated,
            String spent,
            String variance,
            String executionRate
    ) {
    }
}
