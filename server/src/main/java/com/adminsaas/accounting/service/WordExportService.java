package com.adminsaas.accounting.service;

import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFFooter;
import org.apache.poi.xwpf.usermodel.XWPFHeader;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

@Service
public class WordExportService {

    private static final List<LabelDefinition> LABEL_DEFINITIONS = List.of(
            new LabelDefinition("title", "건명", List.of("건명")),
            new LabelDefinition("paymentDate", "결제일자", List.of("결제일자", "결 제 일 자", "지출일", "지출일자", "작성일")),
            new LabelDefinition("vendor", "납품처", List.of("납품처", "납 품 처", "거래처", "판매처")),
            new LabelDefinition("inspector", "검수자", List.of("검수자", "검 수 자")),
            new LabelDefinition("contractAmount", "계약금액", List.of("계약금액", "계 약 금 액", "금액", "총액", "합계")),
            new LabelDefinition("projectName", "프로젝트", List.of("프로젝트", "프로젝트명", "사업명")),
            new LabelDefinition("itemName", "내역", List.of("내역", "항목", "품명")),
            new LabelDefinition("category", "카테고리", List.of("카테고리", "예산 카테고리")),
            new LabelDefinition("subcategory", "세부항목", List.of("세부항목", "하위 카테고리", "세부 항목")),
            new LabelDefinition("paymentMethod", "결제수단", List.of("결제수단", "결제수단명", "지불방법")),
            new LabelDefinition("amount", "금액", List.of("금액", "총액", "합계")),
            new LabelDefinition("notes", "비고", List.of("비고", "메모"))
    );

    private static final List<AliasEntry> SORTED_ALIASES = LABEL_DEFINITIONS.stream()
            .flatMap(definition -> definition.aliases().stream()
                    .map(alias -> new AliasEntry(definition, alias, normalizeForLabelMatch(alias))))
            .sorted(Comparator.comparingInt((AliasEntry entry) -> entry.normalizedAlias().length()).reversed())
            .toList();

    private final FileStorageService fileStorageService;

    public WordExportService(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    public byte[] createEvidenceDocument(EvidenceExportPayload payload) throws IOException {
        Path templatePath = findTemplate(payload.projectId(), payload.templateCategory());
        if (templatePath != null) {
            return createEvidenceDocumentFromTemplate(templatePath, payload);
        }

        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            addTitle(document, "지출 증빙 문서");
            addParagraph(document, "프로젝트", payload.projectName());
            addParagraph(document, "지출일", payload.paymentDate());
            addParagraph(document, "거래처", payload.vendor());
            addParagraph(document, "내역", payload.itemName());
            addParagraph(document, "카테고리", payload.category());
            addParagraph(document, "세부항목", payload.subcategory());
            addParagraph(document, "결제수단", payload.paymentMethod());
            addParagraph(document, "금액", payload.amount());
            addParagraph(document, "비고", payload.notes());
            if (payload.fieldValues() != null && !payload.fieldValues().isEmpty()) {
                for (Map.Entry<String, String> entry : payload.fieldValues().entrySet()) {
                    if (isStandardPlaceholder(entry.getKey())) {
                        continue;
                    }
                    addParagraph(document, entry.getKey(), entry.getValue());
                }
            }

            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    public EvidencePreviewResult createEvidencePreview(EvidenceExportPayload payload) throws IOException {
        byte[] documentBytes = createEvidenceDocument(payload);
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(documentBytes))) {
            return new EvidencePreviewResult(
                    extractParagraphs(document.getBodyElements()),
                    extractTables(document.getTables())
            );
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

    private Path findTemplate(Long projectId, String templateCategory) throws IOException {
        if (projectId == null) {
            return null;
        }

        String category = templateCategory == null || templateCategory.isBlank() ? "evidence-template" : templateCategory;
        return fileStorageService.list(projectId, category).stream()
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
        if (payload.fieldValues() != null) {
            payload.fieldValues().forEach((key, value) -> {
                if (key != null && !key.isBlank()) {
                    placeholders.put(key.trim(), orDash(value));
                }
            });
        }
        if (payload.lineItems() != null) {
            for (int index = 0; index < payload.lineItems().size(); index++) {
                EvidenceLineItem lineItem = payload.lineItems().get(index);
                String prefix = "items." + index + ".";
                placeholders.put(prefix + "name", orDash(lineItem.itemName()));
                placeholders.put(prefix + "spec", orDash(lineItem.spec()));
                placeholders.put(prefix + "quantity", orDash(lineItem.quantity()));
                placeholders.put(prefix + "unit", orDash(lineItem.unit()));
                placeholders.put(prefix + "unitPrice", orDash(lineItem.unitPrice()));
                placeholders.put(prefix + "amount", orDash(lineItem.amount()));
            }
        }
        return placeholders;
    }

    private boolean isStandardPlaceholder(String key) {
        return switch (key) {
            case "projectName", "date", "paymentDate", "vendor", "itemName", "category", "subcategory", "paymentMethod", "amount", "notes" -> true;
            default -> false;
        };
    }

    private void replaceBodyElements(List<IBodyElement> bodyElements, Map<String, String> placeholders) {
        for (IBodyElement element : bodyElements) {
            if (element instanceof XWPFParagraph paragraph) {
                replaceParagraph(paragraph, placeholders);
            } else if (element instanceof XWPFTable table) {
                replaceTable(table, placeholders);
            }
        }
    }

    private void replaceTable(XWPFTable table, Map<String, String> placeholders) {
        replaceItemRows(table, placeholders);
        for (XWPFTableRow row : table.getRows()) {
            List<XWPFTableCell> cells = row.getTableCells();
            if (isLikelyItemHeaderRow(cells)) {
                continue;
            }
            boolean[] handled = new boolean[cells.size()];

            for (int index = 0; index + 1 < cells.size(); index += 2) {
                if (replaceMetadataPair(cells.get(index), cells.get(index + 1), placeholders)) {
                    handled[index] = true;
                    handled[index + 1] = true;
                }
            }

            for (int index = 0; index < cells.size(); index++) {
                if (!handled[index]) {
                    replaceBodyElements(cells.get(index).getBodyElements(), placeholders);
                }
            }
        }
    }

    private void replaceItemRows(XWPFTable table, Map<String, String> placeholders) {
        int headerRowIndex = findItemHeaderRowIndex(table);
        if (headerRowIndex < 0) {
            return;
        }

        List<EvidenceLineItem> lineItems = extractEvidenceLineItems(placeholders);
        if (lineItems.isEmpty()) {
            return;
        }

        for (int rowIndex = headerRowIndex + 1; rowIndex < table.getNumberOfRows(); rowIndex++) {
            XWPFTableRow row = table.getRow(rowIndex);
            EvidenceLineItem lineItem = rowIndex - headerRowIndex - 1 < lineItems.size()
                    ? lineItems.get(rowIndex - headerRowIndex - 1)
                    : null;
            fillEvidenceItemRow(row, lineItem);
        }
    }

    private int findItemHeaderRowIndex(XWPFTable table) {
        for (int rowIndex = 0; rowIndex < table.getNumberOfRows(); rowIndex++) {
            XWPFTableRow row = table.getRow(rowIndex);
            if (row != null && isLikelyItemHeaderRow(row.getTableCells())) {
                return rowIndex;
            }
        }
        return -1;
    }

    private List<EvidenceLineItem> extractEvidenceLineItems(Map<String, String> placeholders) {
        List<EvidenceLineItem> lineItems = new ArrayList<>();
        for (int index = 0; ; index++) {
            String prefix = "items." + index + ".";
            String itemName = placeholders.get(prefix + "name");
            String spec = placeholders.get(prefix + "spec");
            String quantity = placeholders.get(prefix + "quantity");
            String unit = placeholders.get(prefix + "unit");
            String unitPrice = placeholders.get(prefix + "unitPrice");
            String amount = placeholders.get(prefix + "amount");
            if (itemName == null && spec == null && quantity == null && unit == null && unitPrice == null && amount == null) {
                break;
            }
            lineItems.add(new EvidenceLineItem(itemName, spec, quantity, unit, unitPrice, amount));
        }
        return lineItems;
    }

    private void fillEvidenceItemRow(XWPFTableRow row, EvidenceLineItem lineItem) {
        if (row == null) {
            return;
        }
        List<XWPFTableCell> cells = row.getTableCells();
        String[] values = lineItem == null
                ? new String[]{"", "", "", "", "", ""}
                : new String[]{
                lineItem.itemName(),
                lineItem.spec(),
                lineItem.quantity(),
                lineItem.unit(),
                lineItem.unitPrice(),
                lineItem.amount()
        };
        for (int cellIndex = 0; cellIndex < cells.size(); cellIndex++) {
            setCellText(cells.get(cellIndex), orDash(cellIndex < values.length ? values[cellIndex] : ""));
        }
    }

    private boolean isLikelyItemHeaderRow(List<XWPFTableCell> cells) {
        String normalizedRow = cells.stream()
                .map(XWPFTableCell::getText)
                .map(WordExportService::normalizeForLabelMatch)
                .reduce("", String::concat);
        int score = 0;
        if (normalizedRow.contains(normalizeForLabelMatch("\uD488\uBA85"))) score++;
        if (normalizedRow.contains(normalizeForLabelMatch("\uADDC\uACA9"))) score++;
        if (normalizedRow.contains(normalizeForLabelMatch("\uC218\uB7C9"))) score++;
        if (normalizedRow.contains(normalizeForLabelMatch("\uB2E8\uC704"))) score++;
        if (normalizedRow.contains(normalizeForLabelMatch("\uB2E8\uAC00"))) score++;
        if (normalizedRow.contains(normalizeForLabelMatch("\uAE08\uC561"))) score++;
        return score >= 4
                && normalizedRow.contains(normalizeForLabelMatch("\uD488\uBA85"))
                && normalizedRow.contains(normalizeForLabelMatch("\uC218\uB7C9"));
    }

    private boolean replaceMetadataPair(XWPFTableCell labelCell,
                                        XWPFTableCell valueCell,
                                        Map<String, String> placeholders) {
        MatchedLabel matchedLabel = matchKnownLabel(labelCell.getText());
        if (matchedLabel == null) {
            return false;
        }

        setCellText(labelCell, matchedLabel.definition().canonicalLabel());
        String mappedValue = placeholders.get(matchedLabel.definition().fieldKey());
        if (mappedValue == null || mappedValue.isBlank()) {
            String existingValue = valueCell.getText();
            if (existingValue != null && !existingValue.isBlank()) {
                mappedValue = existingValue;
            } else {
                mappedValue = extractFallbackValue(matchedLabel);
            }
        }
        setCellText(valueCell, orDash(mappedValue));
        return true;
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
        replacedText = replaceLabeledText(replacedText, placeholders);

        if (replacedText.equals(originalText)) {
            return;
        }

        while (!paragraph.getRuns().isEmpty()) {
            paragraph.removeRun(0);
        }

        XWPFRun run = paragraph.createRun();
        run.setText(replacedText);
    }

    private String replaceLabeledText(String text, Map<String, String> placeholders) {
        String updated = text;
        for (LabelDefinition definition : LABEL_DEFINITIONS) {
            String value = placeholders.get(definition.fieldKey());
            if (value == null || value.isBlank() || "-".equals(value)) {
                continue;
            }
            for (String alias : definition.aliases()) {
                updated = replaceAfterLabel(updated, alias, value);
            }
        }
        return updated;
    }

    private String replaceAfterLabel(String text, String label, String value) {
        String pattern = "(" + Pattern.quote(label) + "\\s*[:\\s]*)([^\\n\\r]*)";
        return text.replaceAll(pattern, "$1" + java.util.regex.Matcher.quoteReplacement(value));
    }

    private static String normalizeForLabelMatch(String text) {
        if (text == null) {
            return "";
        }
        return text.trim()
                .replaceAll("\\s+", "")
                .replaceAll("[\\p{Punct}·•ㆍ]", "")
                .toLowerCase();
    }

    private MatchedLabel matchKnownLabel(String rawText) {
        String normalized = normalizeForLabelMatch(rawText);
        if (normalized.isEmpty()) {
            return null;
        }

        for (AliasEntry aliasEntry : SORTED_ALIASES) {
            if (normalized.startsWith(aliasEntry.normalizedAlias())) {
                String trailing = normalized.substring(aliasEntry.normalizedAlias().length()).trim();
                return new MatchedLabel(aliasEntry.definition(), rawText == null ? "" : rawText, trailing);
            }
        }
        return null;
    }

    private String extractFallbackValue(MatchedLabel matchedLabel) {
        return matchedLabel.normalizedTrailingText().isBlank() ? null : matchedLabel.normalizedTrailingText();
    }

    private void setCellText(XWPFTableCell cell, String text) {
        for (int index = cell.getParagraphs().size() - 1; index >= 0; index--) {
            cell.removeParagraph(index);
        }
        cell.setText(text == null ? "" : text);
    }

    private List<String> extractParagraphs(List<IBodyElement> bodyElements) {
        ArrayList<String> paragraphs = new ArrayList<>();
        for (IBodyElement element : bodyElements) {
            if (element instanceof XWPFParagraph paragraph) {
                String text = paragraph.getText();
                if (text != null && !text.isBlank()) {
                    paragraphs.add(text);
                }
            }
        }
        return paragraphs;
    }

    private List<List<List<String>>> extractTables(List<XWPFTable> tables) {
        ArrayList<List<List<String>>> extracted = new ArrayList<>();
        for (XWPFTable table : tables) {
            ArrayList<List<String>> rows = new ArrayList<>();
            for (XWPFTableRow row : table.getRows()) {
                ArrayList<String> cells = new ArrayList<>();
                row.getTableCells().forEach(cell -> cells.add(cell.getText()));
                rows.add(cells);
            }
            extracted.add(rows);
        }
        return extracted;
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
            String templateCategory,
            String projectName,
            String paymentDate,
            String vendor,
            String itemName,
            String category,
            String subcategory,
            String paymentMethod,
            String amount,
            String notes,
            Map<String, String> fieldValues,
            List<EvidenceLineItem> lineItems
    ) {
    }

    public record EvidenceLineItem(
            String itemName,
            String spec,
            String quantity,
            String unit,
            String unitPrice,
            String amount
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

    public record EvidencePreviewResult(
            List<String> paragraphs,
            List<List<List<String>>> tables
    ) {
    }

    private record LabelDefinition(
            String fieldKey,
            String canonicalLabel,
            List<String> aliases
    ) {
    }

    private record AliasEntry(
            LabelDefinition definition,
            String alias,
            String normalizedAlias
    ) {
    }

    private record MatchedLabel(
            LabelDefinition definition,
            String rawText,
            String normalizedTrailingText
    ) {
    }
}
