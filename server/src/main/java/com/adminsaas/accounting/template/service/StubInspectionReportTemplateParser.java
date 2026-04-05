package com.adminsaas.accounting.template.service;

import com.adminsaas.accounting.template.domain.NormalizedBlock;
import com.adminsaas.accounting.template.domain.NormalizedLabel;
import com.adminsaas.accounting.template.domain.NormalizedTable;
import com.adminsaas.accounting.template.domain.NormalizedTemplateContent;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@Component
public class StubInspectionReportTemplateParser implements TemplateContentParser {

    private static final String LABEL_TITLE = "\uAC74\uBA85";
    private static final String LABEL_DATE = "\uACB0\uC81C\uC77C\uC790";
    private static final String LABEL_VENDOR = "\uB0A9\uD488\uCC98";
    private static final String LABEL_INSPECTOR = "\uAC80\uC218\uC790";
    private static final String LABEL_CONTRACT_AMOUNT = "\uACC4\uC57D\uAE08\uC561";
    private static final String TABLE_METADATA = "\uAE30\uBCF8 \uC815\uBCF4";
    private static final String TABLE_ITEMS = "\uBB3C\uD488 \uAC80\uC218 \uB0B4\uC5ED";
    private static final String COLUMN_NAME = "\uD488\uBA85";
    private static final String COLUMN_SPEC = "\uADDC\uACA9";
    private static final String COLUMN_QUANTITY = "\uC218\uB7C9";
    private static final String COLUMN_UNIT = "\uB2E8\uC704";
    private static final String COLUMN_UNIT_PRICE = "\uB2E8\uAC00";
    private static final String COLUMN_AMOUNT = "\uAE08\uC561";

    private static final List<String> SUPPORTED_EXTENSIONS = List.of(".doc", ".docx", ".hwp", ".hwpx");
    private static final List<String> KNOWN_LABELS = List.of(
            LABEL_TITLE,
            LABEL_DATE,
            LABEL_VENDOR,
            LABEL_INSPECTOR,
            LABEL_CONTRACT_AMOUNT
    );
    private static final List<String> SORTED_KNOWN_LABELS = KNOWN_LABELS.stream()
            .sorted(Comparator.comparingInt((String value) -> normalizeForLabelMatch(value).length()).reversed())
            .toList();
    private static final List<String> KNOWN_HEADERS = List.of(
            COLUMN_NAME,
            COLUMN_SPEC,
            COLUMN_QUANTITY,
            COLUMN_UNIT,
            COLUMN_UNIT_PRICE,
            COLUMN_AMOUNT
    );
    private static final List<String> METADATA_HEADERS = List.of("label", "value", "label", "value");
    private static final Pattern XML_TABLE_ROW_PATTERN = Pattern.compile("<[^>]*tr[^>]*>(.*?)</[^>]*tr>", Pattern.DOTALL);
    private static final Pattern XML_TABLE_CELL_PATTERN = Pattern.compile("<[^>]*t[cdh][^>]*>(.*?)</[^>]*t[cdh]>", Pattern.DOTALL);
    private static final Pattern XML_TEXT_PATTERN = Pattern.compile("<[^>]*t[^>]*>(.*?)</[^>]*t>", Pattern.DOTALL);
    private static final Pattern LEADING_VALUE_SEPARATOR_PATTERN = Pattern.compile("^[\\s:：\\-_/.,()\\[\\]{}]+");

    @Override
    public boolean supports(MultipartFile file) {
        String filename = file.getOriginalFilename();
        if (filename == null) {
            return false;
        }
        String lowercase = filename.toLowerCase(Locale.ROOT);
        return SUPPORTED_EXTENSIONS.stream().anyMatch(lowercase::endsWith);
    }

    @Override
    public NormalizedTemplateContent parse(MultipartFile file) throws IOException {
        String filename = file.getOriginalFilename() == null ? "" : file.getOriginalFilename().toLowerCase(Locale.ROOT);
        if (filename.endsWith(".docx")) {
            return parseDocx(file);
        }
        if (filename.endsWith(".hwpx")) {
            return parseHwpx(file);
        }
        return parseBinaryBestEffort(file);
    }

    private NormalizedTemplateContent parseDocx(MultipartFile file) throws IOException {
        List<String> textLines = new ArrayList<>();
        List<List<String>> rawRows = new ArrayList<>();

        try (ByteArrayInputStream inputStream = new ByteArrayInputStream(file.getBytes()); XWPFDocument document = new XWPFDocument(inputStream)) {
            for (IBodyElement bodyElement : document.getBodyElements()) {
                if (bodyElement instanceof XWPFParagraph paragraph) {
                    String text = normalizeWhitespace(paragraph.getText());
                    if (!text.isBlank()) {
                        textLines.add(text);
                    }
                } else if (bodyElement instanceof XWPFTable table) {
                    rawRows.addAll(extractRows(table));
                }
            }
        }

        return buildInspectionContent(textLines, rawRows);
    }

    private NormalizedTemplateContent parseHwpx(MultipartFile file) throws IOException {
        List<String> textLines = new ArrayList<>();
        List<List<String>> rawRows = new ArrayList<>();

        try (ZipInputStream zipInputStream = new ZipInputStream(new ByteArrayInputStream(file.getBytes()), StandardCharsets.UTF_8)) {
            ZipEntry entry;
            while ((entry = zipInputStream.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().toLowerCase(Locale.ROOT).endsWith(".xml")) {
                    continue;
                }

                String xml = new String(zipInputStream.readAllBytes(), StandardCharsets.UTF_8);
                textLines.addAll(extractXmlTexts(xml));
                for (List<List<String>> tableRows : extractXmlTables(xml)) {
                    rawRows.addAll(tableRows);
                }
            }
        }

        return buildInspectionContent(textLines, rawRows);
    }

    private NormalizedTemplateContent parseBinaryBestEffort(MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        List<String> textLines = extractUtf16Strings(bytes);
        if (textLines.isEmpty()) {
            textLines = extractAsciiStrings(bytes);
        }
        return buildInspectionContent(textLines, List.of());
    }

    private List<List<String>> extractRows(XWPFTable table) {
        List<List<String>> rows = new ArrayList<>();
        for (XWPFTableRow row : table.getRows()) {
            List<String> cells = new ArrayList<>();
            for (XWPFTableCell cell : row.getTableCells()) {
                cells.add(normalizeWhitespace(cell.getText()));
            }
            if (cells.stream().anyMatch(cell -> !cell.isBlank())) {
                rows.add(cells);
            }
        }
        return rows;
    }

    private List<String> extractXmlTexts(String xml) {
        Set<String> texts = new LinkedHashSet<>();
        Matcher matcher = XML_TEXT_PATTERN.matcher(xml);
        while (matcher.find()) {
            String text = stripXmlTags(matcher.group(1));
            if (!text.isBlank()) {
                texts.add(text);
            }
        }
        return texts.stream().toList();
    }

    private List<List<List<String>>> extractXmlTables(String xml) {
        List<List<List<String>>> tables = new ArrayList<>();
        Matcher rowMatcher = XML_TABLE_ROW_PATTERN.matcher(xml);
        List<List<String>> currentTable = new ArrayList<>();

        while (rowMatcher.find()) {
            String rowXml = rowMatcher.group(1);
            Matcher cellMatcher = XML_TABLE_CELL_PATTERN.matcher(rowXml);
            List<String> row = new ArrayList<>();
            while (cellMatcher.find()) {
                row.add(stripXmlTags(cellMatcher.group(1)));
            }
            if (!row.isEmpty() && row.stream().anyMatch(cell -> !cell.isBlank())) {
                currentTable.add(row);
            }
        }

        if (!currentTable.isEmpty()) {
            tables.add(currentTable);
        }
        return tables;
    }

    private List<String> extractUtf16Strings(byte[] bytes) {
        Set<String> results = new LinkedHashSet<>();
        ByteBuffer buffer = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        StringBuilder current = new StringBuilder();

        while (buffer.remaining() >= 2) {
            char character = buffer.getChar();
            if (isLikelyDocumentCharacter(character)) {
                current.append(character);
            } else {
                flushString(results, current);
            }
        }
        flushString(results, current);
        return results.stream().toList();
    }

    private List<String> extractAsciiStrings(byte[] bytes) {
        Set<String> results = new LinkedHashSet<>();
        StringBuilder current = new StringBuilder();
        for (byte raw : bytes) {
            char character = (char) (raw & 0xff);
            if (character >= 32 && character <= 126) {
                current.append(character);
            } else {
                flushString(results, current);
            }
        }
        flushString(results, current);
        return results.stream().toList();
    }

    private void flushString(Set<String> results, StringBuilder current) {
        String candidate = normalizeWhitespace(current.toString());
        if (candidate.length() >= 2) {
            results.add(candidate);
        }
        current.setLength(0);
    }

    private boolean isLikelyDocumentCharacter(char character) {
        return Character.isLetterOrDigit(character)
                || Character.UnicodeBlock.of(character) == Character.UnicodeBlock.HANGUL_SYLLABLES
                || Character.UnicodeBlock.of(character) == Character.UnicodeBlock.HANGUL_COMPATIBILITY_JAMO
                || Character.UnicodeBlock.of(character) == Character.UnicodeBlock.HANGUL_JAMO
                || " ::-_/().,[]{}".indexOf(character) >= 0;
    }

    private NormalizedTemplateContent buildInspectionContent(List<String> textLines, List<List<String>> rawRows) {
        List<String> cleanedLines = textLines.stream()
                .map(this::normalizeWhitespace)
                .filter(line -> !line.isBlank())
                .distinct()
                .toList();

        List<List<ParsedCell>> parsedRows = rawRows.stream()
                .map(this::toParsedCells)
                .filter(row -> row.stream().anyMatch(cell -> !cell.rawText().isBlank()))
                .toList();

        int itemHeaderIndex = findItemHeaderIndex(parsedRows);
        List<List<ParsedCell>> metadataRows = itemHeaderIndex >= 0 ? parsedRows.subList(0, itemHeaderIndex) : parsedRows;
        List<List<String>> cleanedMetadataRows = buildCleanMetadataPreviewRows(metadataRows);
        List<MetadataPair> metadataPairs = buildMetadataPairs(metadataRows);
        NormalizedTable itemsTable = itemHeaderIndex >= 0 ? buildItemsTable(parsedRows, itemHeaderIndex) : buildItemsTableFromLines(cleanedLines);
        Map<String, String> labels = buildDetectedLabels(metadataPairs, cleanedLines);

        List<NormalizedBlock> blocks = new ArrayList<>();
        cleanedLines.forEach(line -> blocks.add(new NormalizedBlock("text", line, List.of())));
        if (!cleanedMetadataRows.isEmpty()) {
            blocks.add(new NormalizedBlock("table", null, cleanedMetadataRows));
        }
        if (itemsTable != null) {
            List<List<String>> rows = new ArrayList<>();
            rows.add(itemsTable.headers());
            rows.addAll(itemsTable.rows());
            blocks.add(new NormalizedBlock("table", null, rows));
        }

        List<NormalizedTable> detectedTables = new ArrayList<>();
        if (!cleanedMetadataRows.isEmpty()) {
            detectedTables.add(new NormalizedTable(TABLE_METADATA, METADATA_HEADERS, cleanedMetadataRows));
        }
        if (itemsTable != null) {
            detectedTables.add(itemsTable);
        }

        return new NormalizedTemplateContent(
                "inspection_report",
                blocks,
                labels.entrySet().stream().map(entry -> new NormalizedLabel(entry.getKey(), entry.getValue())).toList(),
                detectedTables
        );
    }

    private List<ParsedCell> toParsedCells(List<String> rawRow) {
        return rawRow.stream()
                .map(value -> new ParsedCell(normalizeWhitespace(value), normalizeForLabelMatch(value)))
                .toList();
    }

    private int findItemHeaderIndex(List<List<ParsedCell>> parsedRows) {
        for (int i = 0; i < parsedRows.size(); i++) {
            if (matchesHeaders(parsedRows.get(i))) {
                return i;
            }
        }
        return -1;
    }

    private List<List<String>> buildCleanMetadataPreviewRows(List<List<ParsedCell>> metadataRows) {
        List<List<String>> previewRows = new ArrayList<>();
        for (List<ParsedCell> row : metadataRows) {
            List<String> previewRow = new ArrayList<>();
            for (int pairIndex = 0; pairIndex < 2; pairIndex++) {
                int labelIndex = pairIndex * 2;
                ParsedCell labelCell = getCell(row, labelIndex);
                ParsedCell valueCell = getCell(row, labelIndex + 1);
                LabelMatch match = matchKnownLabel(labelCell.rawText());
                if (match != null) {
                    previewRow.add(match.label());
                    previewRow.add(selectPreviewValue(match, valueCell));
                } else {
                    previewRow.add(labelCell.rawText());
                    previewRow.add(valueCell.rawText());
                }
            }
            if (previewRow.stream().anyMatch(cell -> !cell.isBlank())) {
                previewRows.add(previewRow);
            }
        }
        return previewRows;
    }

    private List<MetadataPair> buildMetadataPairs(List<List<ParsedCell>> metadataRows) {
        List<MetadataPair> pairs = new ArrayList<>();
        for (List<ParsedCell> row : metadataRows) {
            for (int pairIndex = 0; pairIndex < 2; pairIndex++) {
                int labelIndex = pairIndex * 2;
                ParsedCell labelCell = getCell(row, labelIndex);
                ParsedCell valueCell = getCell(row, labelIndex + 1);
                LabelMatch match = matchKnownLabel(labelCell.rawText());
                if (match == null) {
                    continue;
                }

                String rawPreviewValue = selectPreviewValue(match, valueCell);
                String mappedValue = isPlaceholderValue(rawPreviewValue) ? "" : rawPreviewValue;
                pairs.add(new MetadataPair(match.label(), labelCell.rawText(), rawPreviewValue, mappedValue));
            }
        }
        return pairs;
    }

    private Map<String, String> buildDetectedLabels(List<MetadataPair> metadataPairs, List<String> lines) {
        Map<String, String> labels = new LinkedHashMap<>();
        for (MetadataPair pair : metadataPairs) {
            labels.putIfAbsent(pair.label(), pair.mappedValue());
        }

        for (String line : lines) {
            LabelMatch match = matchKnownLabel(line);
            if (match == null || labels.containsKey(match.label())) {
                continue;
            }

            String remainder = cleanupValueText(extractTrailingTextAfterLabel(line, match.label()));
            labels.put(match.label(), isPlaceholderValue(remainder) ? "" : remainder);
        }

        return labels;
    }

    private NormalizedTable buildItemsTable(List<List<ParsedCell>> parsedRows, int itemHeaderIndex) {
        List<List<String>> dataRows = new ArrayList<>();
        for (int i = itemHeaderIndex + 1; i < parsedRows.size(); i++) {
            List<String> row = parsedRows.get(i).stream().map(ParsedCell::rawText).toList();
            if (row.size() == KNOWN_HEADERS.size()) {
                dataRows.add(row);
            }
        }
        return new NormalizedTable(TABLE_ITEMS, KNOWN_HEADERS, dataRows);
    }

    private NormalizedTable buildItemsTableFromLines(List<String> lines) {
        boolean titleSeen = lines.stream().anyMatch(line -> normalizeForLabelMatch(line).contains(normalizeForLabelMatch(TABLE_ITEMS)));
        boolean headerSeen = lines.stream().anyMatch(this::containsAllKnownHeaders);
        if (titleSeen || headerSeen) {
            return new NormalizedTable(TABLE_ITEMS, KNOWN_HEADERS, List.of());
        }
        return null;
    }

    private boolean matchesHeaders(List<ParsedCell> row) {
        List<String> normalizedRow = row.stream().map(ParsedCell::normalizedText).toList();
        return KNOWN_HEADERS.stream().map(StubInspectionReportTemplateParser::normalizeForLabelMatch).allMatch(normalizedRow::contains);
    }

    private boolean containsAllKnownHeaders(String line) {
        String normalized = normalizeForLabelMatch(line);
        return KNOWN_HEADERS.stream().map(StubInspectionReportTemplateParser::normalizeForLabelMatch).allMatch(normalized::contains);
    }

    private ParsedCell getCell(List<ParsedCell> row, int index) {
        if (index < row.size()) {
            return row.get(index);
        }
        return new ParsedCell("", "");
    }

    private String selectPreviewValue(LabelMatch match, ParsedCell valueCell) {
        if (!valueCell.rawText().isBlank()) {
            return valueCell.rawText();
        }
        String fallback = cleanupValueText(match.trailingText());
        return fallback;
    }

    private static String normalizeForLabelMatch(String text) {
        if (text == null) {
            return "";
        }

        String trimmed = text.trim();
        StringBuilder builder = new StringBuilder(trimmed.length());
        for (int i = 0; i < trimmed.length(); i++) {
            char character = trimmed.charAt(i);
            if (Character.isWhitespace(character)) {
                continue;
            }
            if (isPunctuationNoise(character)) {
                continue;
            }
            builder.append(Character.toLowerCase(character));
        }
        return builder.toString();
    }

    static LabelMatch matchKnownLabel(String rawText) {
        String normalized = normalizeForLabelMatch(rawText);
        if (normalized.isBlank()) {
            return null;
        }

        for (String knownLabel : SORTED_KNOWN_LABELS) {
            String normalizedKnownLabel = normalizeForLabelMatch(knownLabel);
            if (!normalized.startsWith(normalizedKnownLabel)) {
                continue;
            }
            return new LabelMatch(knownLabel, extractTrailingTextAfterLabel(rawText, knownLabel));
        }
        return null;
    }

    private static String extractTrailingTextAfterLabel(String rawText, String label) {
        String normalizedLabel = normalizeForLabelMatch(label);
        StringBuilder consumed = new StringBuilder();
        int splitIndex = rawText == null ? 0 : rawText.length();

        for (int i = 0; rawText != null && i < rawText.length(); i++) {
            char character = rawText.charAt(i);
            if (!Character.isWhitespace(character) && !isPunctuationNoise(character)) {
                consumed.append(Character.toLowerCase(character));
            }
            if (consumed.length() >= normalizedLabel.length()) {
                splitIndex = i + 1;
                break;
            }
        }

        return splitIndex >= (rawText == null ? 0 : rawText.length()) ? "" : rawText.substring(splitIndex).trim();
    }

    private static boolean isPunctuationNoise(char character) {
        return character == ':' || character == '：' || character == '-' || character == '_' || character == '/'
                || character == '.' || character == ',' || character == '(' || character == ')' || character == '['
                || character == ']' || character == '{' || character == '}' || character == '·' || character == '•'
                || character == 'ㆍ';
    }

    private String cleanupValueText(String rawValue) {
        String trimmed = normalizeWhitespace(rawValue);
        if (trimmed.isBlank()) {
            return "";
        }
        return LEADING_VALUE_SEPARATOR_PATTERN.matcher(trimmed).replaceFirst("");
    }

    private boolean isPlaceholderValue(String value) {
        String normalized = normalizeWhitespace(value);
        if (normalized.isBlank()) {
            return true;
        }
        return "-".equals(normalized) || "o".equalsIgnoreCase(normalized) || "\u3147".equals(normalized);
    }

    private String stripXmlTags(String raw) {
        String withoutTags = raw.replaceAll("<[^>]+>", " ");
        return normalizeWhitespace(withoutTags
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&apos;", "'"));
    }

    private String normalizeWhitespace(String value) {
        if (value == null) {
            return "";
        }
        return value.replace('\u00a0', ' ').replaceAll("\\s+", " ").trim();
    }

    record ParsedCell(String rawText, String normalizedText) {
    }

    record LabelMatch(String label, String trailingText) {
    }

    record MetadataPair(String label, String rawLabelText, String previewValue, String mappedValue) {
    }
}
