package com.adminsaas.accounting.template.service;

import com.adminsaas.accounting.service.FileStorageService;
import com.adminsaas.accounting.template.domain.DocumentTemplate;
import com.adminsaas.accounting.template.domain.InspectionReportExportJob;
import com.adminsaas.accounting.template.domain.InspectionReportExportMode;
import com.adminsaas.accounting.template.domain.InspectionReportGeneratedDocument;
import com.adminsaas.accounting.template.domain.InspectionReportItemRow;
import com.adminsaas.accounting.template.domain.InspectionReportReceiptBlock;
import com.adminsaas.accounting.template.domain.InspectionReportReceiptEntry;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.ParagraphAlignment;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.apache.xmlbeans.XmlCursor;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTP;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.CTTbl;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

@Service
public class InspectionReportWordExportService {

    private static final String DOCX_CONTENT_TYPE = "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final String ZIP_CONTENT_TYPE = "application/zip";
    private static final String DEFAULT_HEAD_TITLE = "물품검수조서";
    private static final int ITEM_COLUMN_COUNT = 6;

    private static final List<LabelDefinition> LABEL_DEFINITIONS = List.of(
            new LabelDefinition("title", "건명", List.of("건명")),
            new LabelDefinition("paymentDate", "결제일자", List.of("결제일자", "결 제 일 자", "지출일자", "작성일")),
            new LabelDefinition("vendor", "납품처", List.of("납품처", "납 품 처", "거래처")),
            new LabelDefinition("inspector", "검수자", List.of("검수자", "검 수 자")),
            new LabelDefinition("contractAmount", "계약금액", List.of("계약금액", "계 약 금 액", "금액", "총액", "합계"))
    );

    private static final List<AliasEntry> SORTED_ALIASES = LABEL_DEFINITIONS.stream()
            .flatMap(definition -> definition.aliases().stream()
                    .map(alias -> new AliasEntry(definition, normalizeForLabelMatch(alias))))
            .sorted(Comparator.comparingInt((AliasEntry entry) -> entry.normalizedAlias().length()).reversed())
            .toList();

    private final InspectionReportGenerationService inspectionReportGenerationService;
    private final FileStorageService fileStorageService;

    public InspectionReportWordExportService(InspectionReportGenerationService inspectionReportGenerationService,
                                             FileStorageService fileStorageService) {
        this.inspectionReportGenerationService = inspectionReportGenerationService;
        this.fileStorageService = fileStorageService;
    }

    public ExportedInspectionReport export(Long projectId,
                                           String relativePath,
                                           DocumentTemplate template,
                                           InspectionReportExportMode mode,
                                           List<InspectionReportReceiptEntry> entries) throws IOException {
        InspectionReportExportJob job = inspectionReportGenerationService.generate(template, mode, entries);
        validateDocxPath(relativePath);
        byte[] templateBytes = fileStorageService.load(projectId, relativePath).content();
        validateTemplateBytes(templateBytes);

        if (job.documents().size() == 1) {
            InspectionReportGeneratedDocument document = job.documents().get(0);
            return new ExportedInspectionReport(
                    sanitizeFilename(document.title()) + ".docx",
                    DOCX_CONTENT_TYPE,
                    renderDocument(templateBytes, document)
            );
        }

        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
             ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream)) {
            for (InspectionReportGeneratedDocument document : job.documents()) {
                zipOutputStream.putNextEntry(new ZipEntry(sanitizeFilename(document.title()) + ".docx"));
                zipOutputStream.write(renderDocument(templateBytes, document));
                zipOutputStream.closeEntry();
            }
            zipOutputStream.finish();
            return new ExportedInspectionReport("inspection-report-export.zip", ZIP_CONTENT_TYPE, outputStream.toByteArray());
        }
    }

    private void validateDocxPath(String relativePath) {
        String lowerCasePath = relativePath == null ? "" : relativePath.toLowerCase();
        if (!lowerCasePath.endsWith(".docx")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "날짜별 저장과 전체 저장은 DOCX 물품검수조서 템플릿에서만 지원됩니다.");
        }
    }

    private void validateTemplateBytes(byte[] templateBytes) {
        if (templateBytes == null || templateBytes.length == 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "물품검수조서 템플릿 파일이 비어 있습니다.");
        }
    }

    private byte[] renderDocument(byte[] templateBytes, InspectionReportGeneratedDocument documentModel) throws IOException {
        try {
            return renderDocumentFromTemplate(templateBytes, documentModel);
        } catch (ResponseStatusException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            return renderFallbackDocument(documentModel);
        }
    }

    private byte[] renderDocumentFromTemplate(byte[] templateBytes,
                                              InspectionReportGeneratedDocument documentModel) throws IOException {
        try (XWPFDocument sourceTemplate = new XWPFDocument(new ByteArrayInputStream(templateBytes));
             XWPFDocument workingDocument = new XWPFDocument(new ByteArrayInputStream(templateBytes));
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            BlockRange sourceRange = findReceiptBlockRange(sourceTemplate);
            BlockRange workingRange = findReceiptBlockRange(workingDocument);

            if (sourceRange == null || workingRange == null || documentModel.receiptBlocks().isEmpty()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "물품검수조서 템플릿 구조를 인식하지 못했습니다.");
            }

            List<IBodyElement> sourceBlockElements = new ArrayList<>(
                    sourceTemplate.getBodyElements().subList(sourceRange.startIndex(), sourceRange.endIndex() + 1)
            );
            fillReceiptBlock(
                    workingDocument.getBodyElements().subList(workingRange.startIndex(), workingRange.endIndex() + 1),
                    documentModel.receiptBlocks().get(0)
            );

            int anchorIndex = workingRange.endIndex();
            for (int blockIndex = 1; blockIndex < documentModel.receiptBlocks().size(); blockIndex++) {
                List<IBodyElement> insertedElements = insertBlockAfter(workingDocument, anchorIndex, sourceBlockElements);
                if (insertedElements.isEmpty()) {
                    throw new IllegalStateException("반복 블록을 추가하지 못했습니다.");
                }
                fillReceiptBlock(insertedElements, documentModel.receiptBlocks().get(blockIndex));
                anchorIndex += insertedElements.size();
            }

            workingDocument.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private byte[] renderFallbackDocument(InspectionReportGeneratedDocument documentModel) throws IOException {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            XWPFParagraph titleParagraph = document.createParagraph();
            titleParagraph.setAlignment(ParagraphAlignment.CENTER);
            XWPFRun titleRun = titleParagraph.createRun();
            titleRun.setBold(true);
            titleRun.setFontSize(14);
            titleRun.setText(documentModel.head() == null || documentModel.head().title() == null || documentModel.head().title().isBlank()
                    ? DEFAULT_HEAD_TITLE
                    : documentModel.head().title());

            for (InspectionReportReceiptBlock block : documentModel.receiptBlocks()) {
                XWPFParagraph spacer = document.createParagraph();
                spacer.createRun().setText("");

                XWPFTable metadataTable = document.createTable(3, 4);
                fillMetadataRow(metadataTable.getRow(0), "건명", block.title(), "결제일자", valueOrDash(block.normalizedPaymentDate(), block.paymentDate()));
                fillMetadataRow(metadataTable.getRow(1), "납품처", block.vendor(), "검수자", block.inspector());
                fillMetadataRow(metadataTable.getRow(2), "계약금액", block.contractAmount(), "-", "-");

                XWPFTable itemTable = document.createTable(1, ITEM_COLUMN_COUNT);
                fillItemHeader(itemTable.getRow(0));
                List<InspectionReportItemRow> itemRows = block.itemRows() == null || block.itemRows().isEmpty()
                        ? List.of(new InspectionReportItemRow("-", "-", "-", "-", "-", "-"))
                        : block.itemRows();
                for (InspectionReportItemRow itemRow : itemRows) {
                    fillItemRow(itemTable.createRow(), itemRow);
                }
            }

            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }

    private void fillMetadataRow(XWPFTableRow row,
                                 String leftLabel,
                                 String leftValue,
                                 String rightLabel,
                                 String rightValue) {
        ensureCellCount(row, 4);
        setCellText(row.getCell(0), leftLabel);
        setCellText(row.getCell(1), valueOrDash(leftValue));
        setCellText(row.getCell(2), rightLabel);
        setCellText(row.getCell(3), valueOrDash(rightValue));
    }

    private void fillItemHeader(XWPFTableRow row) {
        ensureCellCount(row, ITEM_COLUMN_COUNT);
        setCellText(row.getCell(0), "품명");
        setCellText(row.getCell(1), "규격");
        setCellText(row.getCell(2), "수량");
        setCellText(row.getCell(3), "단위");
        setCellText(row.getCell(4), "단가");
        setCellText(row.getCell(5), "금액");
    }

    private BlockRange findReceiptBlockRange(XWPFDocument document) {
        List<IBodyElement> bodyElements = document.getBodyElements();
        int startIndex = -1;
        int endIndex = -1;
        int firstTableIndex = -1;
        int lastTableBeforeItemIndex = -1;

        for (int index = 0; index < bodyElements.size(); index++) {
            IBodyElement element = bodyElements.get(index);
            if (element instanceof XWPFTable table) {
                if (firstTableIndex < 0) {
                    firstTableIndex = index;
                }
                if (startIndex < 0 && isMetadataTable(table)) {
                    startIndex = index;
                }
                if (isItemTable(table)) {
                    endIndex = index;
                    if (startIndex < 0) {
                        startIndex = lastTableBeforeItemIndex >= 0
                                ? lastTableBeforeItemIndex
                                : (firstTableIndex >= 0 ? firstTableIndex : index);
                    }
                    break;
                }
                lastTableBeforeItemIndex = index;
            }
        }

        if (startIndex < 0 && firstTableIndex >= 0) {
            startIndex = firstTableIndex;
        }
        if (endIndex < 0 && lastTableBeforeItemIndex >= 0) {
            endIndex = lastTableBeforeItemIndex;
        }
        if (startIndex < 0 || endIndex < startIndex) {
            return null;
        }
        return new BlockRange(startIndex, endIndex);
    }

    private boolean isMetadataTable(XWPFTable table) {
        long matchedCells = table.getRows().stream()
                .flatMap(row -> row.getTableCells().stream())
                .map(XWPFTableCell::getText)
                .map(InspectionReportWordExportService::matchKnownLabel)
                .filter(match -> match != null)
                .count();
        return matchedCells >= 2;
    }

    private boolean isItemTable(XWPFTable table) {
        if (table.getRows().isEmpty()) {
            return false;
        }
        return isLikelyItemHeaderRow(table.getRow(0).getTableCells());
    }

    private List<IBodyElement> insertBlockAfter(XWPFDocument document, int anchorIndex, List<IBodyElement> sourceElements) {
        List<IBodyElement> bodyElements = document.getBodyElements();
        if (anchorIndex < 0 || anchorIndex >= bodyElements.size()) {
            throw new IllegalArgumentException("반복 블록을 추가할 기준 위치가 올바르지 않습니다.");
        }

        IBodyElement anchor = bodyElements.get(anchorIndex);
        XmlCursor cursor = anchor instanceof XWPFTable table
                ? table.getCTTbl().newCursor()
                : ((XWPFParagraph) anchor).getCTP().newCursor();
        cursor.toEndToken();

        int insertedStartIndex = anchorIndex + 1;
        int insertedCount = 0;
        for (IBodyElement sourceElement : sourceElements) {
            if (sourceElement instanceof XWPFParagraph sourceParagraph) {
                XWPFParagraph newParagraph = document.insertNewParagraph(cursor);
                newParagraph.getCTP().set((CTP) sourceParagraph.getCTP().copy());
                cursor = newParagraph.getCTP().newCursor();
                cursor.toEndToken();
                insertedCount++;
            } else if (sourceElement instanceof XWPFTable sourceTable) {
                XWPFTable newTable = document.insertNewTbl(cursor);
                newTable.getCTTbl().set((CTTbl) sourceTable.getCTTbl().copy());
                cursor = newTable.getCTTbl().newCursor();
                cursor.toEndToken();
                insertedCount++;
            }
        }

        if (insertedCount == 0) {
            return List.of();
        }
        return new ArrayList<>(document.getBodyElements().subList(insertedStartIndex, insertedStartIndex + insertedCount));
    }

    private void fillReceiptBlock(List<IBodyElement> blockElements, InspectionReportReceiptBlock block) {
        Map<String, String> placeholders = buildPlaceholders(block);
        for (IBodyElement element : blockElements) {
            if (element instanceof XWPFParagraph paragraph) {
                replaceParagraph(paragraph, placeholders);
            } else if (element instanceof XWPFTable table) {
                replaceTable(table, placeholders);
            }
        }
    }

    private Map<String, String> buildPlaceholders(InspectionReportReceiptBlock block) {
        Map<String, String> placeholders = new LinkedHashMap<>();
        placeholders.put("title", valueOrDash(block.title()));
        placeholders.put("date", valueOrDash(block.normalizedPaymentDate(), block.paymentDate()));
        placeholders.put("paymentDate", valueOrDash(block.normalizedPaymentDate(), block.paymentDate()));
        placeholders.put("vendor", valueOrDash(block.vendor()));
        placeholders.put("inspector", valueOrDash(block.inspector()));
        placeholders.put("contractAmount", valueOrDash(block.contractAmount()));

        List<InspectionReportItemRow> itemRows = block.itemRows() == null ? List.of() : block.itemRows();
        for (int index = 0; index < itemRows.size(); index++) {
            InspectionReportItemRow row = itemRows.get(index);
            String prefix = "items." + index + ".";
            placeholders.put(prefix + "name", valueOrDash(row.itemName()));
            placeholders.put(prefix + "spec", valueOrDash(row.spec()));
            placeholders.put(prefix + "quantity", valueOrDash(row.quantity()));
            placeholders.put(prefix + "unit", valueOrDash(row.unit()));
            placeholders.put(prefix + "unitPrice", valueOrDash(row.unitPrice()));
            placeholders.put(prefix + "amount", valueOrDash(row.amount()));
        }
        return placeholders;
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

        while (!paragraph.getRuns().isEmpty()) {
            paragraph.removeRun(0);
        }

        XWPFRun run = paragraph.createRun();
        run.setText(replacedText);
    }

    private void replaceTable(XWPFTable table, Map<String, String> placeholders) {
        replaceItemRows(table, placeholders);
        for (XWPFTableRow row : table.getRows()) {
            List<XWPFTableCell> cells = row.getTableCells();
            if (isLikelyItemHeaderRow(cells)) {
                continue;
            }
            for (int index = 0; index + 1 < cells.size(); index += 2) {
                replaceMetadataPair(cells.get(index), cells.get(index + 1), placeholders);
            }
        }
    }

    private void replaceItemRows(XWPFTable table, Map<String, String> placeholders) {
        int headerRowIndex = findItemHeaderRowIndex(table);
        if (headerRowIndex < 0) {
            return;
        }

        List<InspectionReportItemRow> lineItems = extractLineItems(placeholders);
        if (lineItems.isEmpty()) {
            lineItems = List.of(new InspectionReportItemRow("-", "-", "-", "-", "-", "-"));
        }

        int existingDataRowCount = Math.max(0, table.getNumberOfRows() - headerRowIndex - 1);
        while (existingDataRowCount < lineItems.size()) {
            table.createRow();
            existingDataRowCount++;
        }

        for (int rowIndex = headerRowIndex + 1; rowIndex < table.getNumberOfRows(); rowIndex++) {
            XWPFTableRow row = table.getRow(rowIndex);
            InspectionReportItemRow itemRow = rowIndex - headerRowIndex - 1 < lineItems.size()
                    ? lineItems.get(rowIndex - headerRowIndex - 1)
                    : new InspectionReportItemRow("-", "-", "-", "-", "-", "-");
            fillItemRow(row, itemRow);
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

    private List<InspectionReportItemRow> extractLineItems(Map<String, String> placeholders) {
        List<InspectionReportItemRow> lineItems = new ArrayList<>();
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
            lineItems.add(new InspectionReportItemRow(itemName, spec, quantity, unit, unitPrice, amount));
        }
        return lineItems;
    }

    private void fillItemRow(XWPFTableRow row, InspectionReportItemRow itemRow) {
        if (row == null) {
            return;
        }
        ensureCellCount(row, ITEM_COLUMN_COUNT);
        setCellText(row.getCell(0), valueOrDash(itemRow.itemName()));
        setCellText(row.getCell(1), valueOrDash(itemRow.spec()));
        setCellText(row.getCell(2), valueOrDash(itemRow.quantity()));
        setCellText(row.getCell(3), valueOrDash(itemRow.unit()));
        setCellText(row.getCell(4), valueOrDash(itemRow.unitPrice()));
        setCellText(row.getCell(5), valueOrDash(itemRow.amount()));
    }

    private void ensureCellCount(XWPFTableRow row, int expectedCount) {
        while (row.getTableCells().size() < expectedCount) {
            row.addNewTableCell();
        }
    }

    private boolean replaceMetadataPair(XWPFTableCell labelCell,
                                        XWPFTableCell valueCell,
                                        Map<String, String> placeholders) {
        if (labelCell == null || valueCell == null) {
            return false;
        }

        MatchedLabel matchedLabel = matchKnownLabel(labelCell.getText());
        if (matchedLabel == null) {
            return false;
        }

        setCellText(labelCell, matchedLabel.definition().canonicalLabel());
        String mappedValue = placeholders.get(matchedLabel.definition().fieldKey());
        setCellText(valueCell, valueOrDash(mappedValue));
        return true;
    }

    private boolean isLikelyItemHeaderRow(List<XWPFTableCell> cells) {
        String normalizedRow = cells.stream()
                .map(XWPFTableCell::getText)
                .map(InspectionReportWordExportService::normalizeForLabelMatch)
                .reduce("", String::concat);
        int score = 0;
        if (normalizedRow.contains(normalizeForLabelMatch("품명"))) score++;
        if (normalizedRow.contains(normalizeForLabelMatch("규격"))) score++;
        if (normalizedRow.contains(normalizeForLabelMatch("수량"))) score++;
        if (normalizedRow.contains(normalizeForLabelMatch("단위"))) score++;
        if (normalizedRow.contains(normalizeForLabelMatch("단가"))) score++;
        if (normalizedRow.contains(normalizeForLabelMatch("금액"))) score++;
        return score >= 4;
    }

    private static String normalizeForLabelMatch(String text) {
        if (text == null) {
            return "";
        }
        return text.trim()
                .replaceAll("\\s+", "")
                .replaceAll("[\\p{Punct}·ㆍ•…\\-_/]+", "")
                .toLowerCase();
    }

    private static MatchedLabel matchKnownLabel(String rawText) {
        String normalized = normalizeForLabelMatch(rawText);
        if (normalized.isBlank()) {
            return null;
        }
        for (AliasEntry aliasEntry : SORTED_ALIASES) {
            if (normalized.startsWith(aliasEntry.normalizedAlias())) {
                return new MatchedLabel(aliasEntry.definition());
            }
        }
        return null;
    }

    private void setCellText(XWPFTableCell cell, String text) {
        while (cell.getParagraphs().size() > 1) {
            cell.removeParagraph(cell.getParagraphs().size() - 1);
        }
        if (cell.getParagraphs().isEmpty()) {
            cell.addParagraph();
        }
        XWPFParagraph paragraph = cell.getParagraphs().get(0);
        while (!paragraph.getRuns().isEmpty()) {
            paragraph.removeRun(0);
        }
        XWPFRun run = paragraph.createRun();
        run.setText(text == null ? "" : text);
    }

    private String sanitizeFilename(String value) {
        String fallback = value == null || value.isBlank() ? "inspection-report" : value;
        return fallback.replaceAll("[\\\\/:*?\"<>|]", "_");
    }

    private String valueOrDash(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String valueOrDash(String primary, String fallback) {
        return primary != null && !primary.isBlank() ? primary : valueOrDash(fallback);
    }

    public record ExportedInspectionReport(
            String filename,
            String contentType,
            byte[] content
    ) {
    }

    private record BlockRange(
            int startIndex,
            int endIndex
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
            String normalizedAlias
    ) {
    }

    private record MatchedLabel(
            LabelDefinition definition
    ) {
    }
}
