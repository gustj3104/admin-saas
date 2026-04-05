package com.adminsaas.accounting.template.service;

import com.adminsaas.accounting.template.domain.NormalizedTemplateContent;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class StubInspectionReportTemplateParserTest {

    private static final String LABEL_TITLE = "\uAC74\uBA85";
    private static final String LABEL_DATE = "\uACB0\uC81C\uC77C\uC790";
    private static final String LABEL_VENDOR = "\uB0A9\uD488\uCC98";
    private static final String LABEL_INSPECTOR = "\uAC80\uC218\uC790";
    private static final String LABEL_CONTRACT_AMOUNT = "\uACC4\uC57D\uAE08\uC561";
    private static final String TABLE_METADATA = "\uAE30\uBCF8 \uC815\uBCF4";
    private static final String TABLE_ITEMS = "\uBB3C\uD488 \uAC80\uC218 \uB0B4\uC5ED";
    private static final String ITEM_NOTEBOOK = "\uB178\uD2B8\uBD81";

    private final StubInspectionReportTemplateParser parser = new StubInspectionReportTemplateParser();

    @Test
    void case1RepairsPollutedDateLabelAndStillMapsTitleAndDate() throws IOException {
        NormalizedTemplateContent content = parseHwpxTableRows(List.of(
                List.of(LABEL_TITLE, "-", LABEL_DATE + "\u3147", "-")
        ));

        assertThat(content.labels()).extracting(label -> label.label())
                .contains(LABEL_TITLE, LABEL_DATE);
        assertThat(content.labels()).extracting(label -> label.label() + ":" + label.value())
                .contains(LABEL_TITLE + ":", LABEL_DATE + ":");
        assertThat(content.tables().get(0).title()).isEqualTo(TABLE_METADATA);
        assertThat(content.tables().get(0).rows()).containsExactly(
                List.of(LABEL_TITLE, "-", LABEL_DATE, "-")
        );
    }

    @Test
    void case2MatchesWhitespaceInsensitiveVendorAndInspectorLabels() throws IOException {
        NormalizedTemplateContent content = parseHwpxTableRows(List.of(
                List.of("\uB0A9 \uD488 \uCC98", "2 sa", "\uAC80 \uC218 \uC790", "-")
        ));

        assertThat(content.labels()).extracting(label -> label.label() + ":" + label.value())
                .contains(LABEL_VENDOR + ":2 sa", LABEL_INSPECTOR + ":");
        assertThat(content.tables().get(0).rows()).containsExactly(
                List.of(LABEL_VENDOR, "2 sa", LABEL_INSPECTOR, "-")
        );
    }

    @Test
    void case3UsesAdjacentValueWhenLabelCellContainsKnownLabelPlusValue() throws IOException {
        NormalizedTemplateContent content = parseHwpxTableRows(List.of(
                List.of(LABEL_CONTRACT_AMOUNT + "1201019003", "1201019003", "-", "-")
        ));

        assertThat(content.labels()).extracting(label -> label.label() + ":" + label.value())
                .contains(LABEL_CONTRACT_AMOUNT + ":1201019003");
        assertThat(content.tables().get(0).rows()).containsExactly(
                List.of(LABEL_CONTRACT_AMOUNT, "1201019003", "-", "-")
        );
    }

    @Test
    void case4MapsInspectorEvenWhenValueIsPlaceholderOrEmpty() throws IOException {
        NormalizedTemplateContent content = parseHwpxTableRows(List.of(
                List.of(LABEL_INSPECTOR + "-", "-", "", "")
        ));

        assertThat(content.labels()).extracting(label -> label.label() + ":" + label.value())
                .contains(LABEL_INSPECTOR + ":");
        assertThat(content.tables().get(0).rows()).containsExactly(
                List.of(LABEL_INSPECTOR, "-", "", "")
        );
    }

    @Test
    void preservesInspectionItemsTableSeparatelyFromMetadataTable() throws IOException {
        NormalizedTemplateContent content = parseHwpxTableRows(List.of(
                List.of(LABEL_TITLE, "-", LABEL_DATE, "-"),
                List.of("\uD488\uBA85", "\uADDC\uACA9", "\uC218\uB7C9", "\uB2E8\uC704", "\uB2E8\uAC00", "\uAE08\uC561"),
                List.of(ITEM_NOTEBOOK, "15\uC778\uCE58", "2", "\uB300", "1200000", "2400000")
        ));

        assertThat(content.tables()).hasSize(2);
        assertThat(content.tables().get(0).title()).isEqualTo(TABLE_METADATA);
        assertThat(content.tables().get(1).title()).isEqualTo(TABLE_ITEMS);
        assertThat(content.tables().get(1).rows()).containsExactly(
                List.of(ITEM_NOTEBOOK, "15\uC778\uCE58", "2", "\uB300", "1200000", "2400000")
        );
    }

    @Test
    void matchesKnownLabelsByNormalizedPrefix() {
        StubInspectionReportTemplateParser.LabelMatch dateMatch = StubInspectionReportTemplateParser.matchKnownLabel("\uACB0 \uC81C \uC77C \uC790\u3147");
        StubInspectionReportTemplateParser.LabelMatch amountMatch = StubInspectionReportTemplateParser.matchKnownLabel("\uACC4\uC57D\uAE08\uC5611201019003");

        assertThat(dateMatch).isNotNull();
        assertThat(dateMatch.label()).isEqualTo(LABEL_DATE);
        assertThat(amountMatch).isNotNull();
        assertThat(amountMatch.label()).isEqualTo(LABEL_CONTRACT_AMOUNT);
    }

    private NormalizedTemplateContent parseHwpxTableRows(List<List<String>> rows) throws IOException {
        StringBuilder xml = new StringBuilder("<root><hp:tbl>");
        for (List<String> row : rows) {
            xml.append("<hp:tr>");
            for (String cell : row) {
                xml.append("<hp:tc><hp:t>").append(cell).append("</hp:t></hp:tc>");
            }
            xml.append("</hp:tr>");
        }
        xml.append("</hp:tbl></root>");

        MockMultipartFile file = new MockMultipartFile(
                "file",
                "inspection.hwpx",
                "application/octet-stream",
                zipWithXml("Contents/section0.xml", xml.toString())
        );
        return parser.parse(file);
    }

    private byte[] zipWithXml(String entryName, String xml) throws IOException {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        try (ZipOutputStream zipOutputStream = new ZipOutputStream(outputStream, StandardCharsets.UTF_8)) {
            zipOutputStream.putNextEntry(new ZipEntry(entryName));
            zipOutputStream.write(xml.getBytes(StandardCharsets.UTF_8));
            zipOutputStream.closeEntry();
        }
        return outputStream.toByteArray();
    }
}
