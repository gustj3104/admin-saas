package com.adminsaas.accounting.service;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class WordExportServiceTest {

    @TempDir
    Path tempDir;

    @Test
    void previewCleansInspectionMetadataLabelsAndKeepsSeparateValueCells() throws Exception {
        Path template = createTemplate(
                List.of("건명", "-", "결제일자ㅇ", "-"),
                List.of("납 품 처", "2 sa", "검 수 자", "-"),
                List.of("계약금액1201019003", "1201019003", "-", "-")
        );

        WordExportService service = new WordExportService(mockTemplateStorage(template));

        WordExportService.EvidencePreviewResult preview = service.createEvidencePreview(payload());

        assertEquals(
                List.of(
                        List.of("건명", "-", "결제일자", "-"),
                        List.of("납품처", "2 sa", "검수자", "-"),
                        List.of("계약금액", "1201019003", "-", "-")
                ),
                preview.tables().get(0)
        );
    }

    @Test
    void wordDownloadWritesCleanLabelsWithoutDuplicatingMergedContractAmount() throws Exception {
        Path template = createTemplate(
                List.of("건명", "-", "결제일자ㅇ", "-"),
                List.of("납 품 처", "2 sa", "검 수 자", "-"),
                List.of("계약금액1201019003", "1201019003", "-", "-"),
                List.of("검수자-", "-", "", "")
        );

        WordExportService service = new WordExportService(mockTemplateStorage(template));

        byte[] documentBytes = service.createEvidenceDocument(payload());

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(documentBytes))) {
            XWPFTable table = document.getTables().get(0);

            assertEquals("건명", table.getRow(0).getCell(0).getText());
            assertEquals("-", table.getRow(0).getCell(1).getText());
            assertEquals("결제일자", table.getRow(0).getCell(2).getText());
            assertEquals("-", table.getRow(0).getCell(3).getText());

            assertEquals("납품처", table.getRow(1).getCell(0).getText());
            assertEquals("2 sa", table.getRow(1).getCell(1).getText());
            assertEquals("검수자", table.getRow(1).getCell(2).getText());
            assertEquals("-", table.getRow(1).getCell(3).getText());

            assertEquals("계약금액", table.getRow(2).getCell(0).getText());
            assertEquals("1201019003", table.getRow(2).getCell(1).getText());
            assertEquals("-", table.getRow(2).getCell(2).getText());
            assertEquals("-", table.getRow(2).getCell(3).getText());

            assertEquals("검수자", table.getRow(3).getCell(0).getText());
            assertEquals("-", table.getRow(3).getCell(1).getText());
        }
    }

    @Test
    void keepsItemHeaderRowUntouchedAndStartsReplacingFromSecondRow() throws Exception {
        Path template = createTemplate(
                List.of("\uD488\uBA85", "\uADDC\uACA9", "\uC218\uB7C9", "\uB2E8\uC704", "\uB2E8\uAC00", "\uAE08\uC561"),
                List.of("-", "-", "-", "-", "-", "-"),
                List.of("-", "-", "-", "-", "-", "-")
        );

        WordExportService service = new WordExportService(mockTemplateStorage(template));

        byte[] documentBytes = service.createEvidenceDocument(payloadWithItems());

        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(documentBytes))) {
            XWPFTable table = document.getTables().get(0);

            assertEquals("\uD488\uBA85", table.getRow(0).getCell(0).getText());
            assertEquals("\uADDC\uACA9", table.getRow(0).getCell(1).getText());
            assertEquals("\uC218\uB7C9", table.getRow(0).getCell(2).getText());
            assertEquals("\uB2E8\uC704", table.getRow(0).getCell(3).getText());
            assertEquals("\uB2E8\uAC00", table.getRow(0).getCell(4).getText());
            assertEquals("\uAE08\uC561", table.getRow(0).getCell(5).getText());

            assertEquals("A4\uC6A9\uC9C0", table.getRow(1).getCell(0).getText());
            assertEquals("80g", table.getRow(1).getCell(1).getText());
            assertEquals("2", table.getRow(1).getCell(2).getText());
            assertEquals("EA", table.getRow(1).getCell(3).getText());
            assertEquals("3500", table.getRow(1).getCell(4).getText());
            assertEquals("7000", table.getRow(1).getCell(5).getText());

            assertEquals("\uBCFC\uD39C", table.getRow(2).getCell(0).getText());
            assertEquals("0.7", table.getRow(2).getCell(1).getText());
            assertEquals("3", table.getRow(2).getCell(2).getText());
            assertEquals("EA", table.getRow(2).getCell(3).getText());
            assertEquals("1000", table.getRow(2).getCell(4).getText());
            assertEquals("3000", table.getRow(2).getCell(5).getText());
        }
    }

    private FileStorageService mockTemplateStorage(Path templatePath) throws Exception {
        FileStorageService fileStorageService = mock(FileStorageService.class);
        when(fileStorageService.list(4L, "evidence-template")).thenReturn(List.of(
                new FileStorageService.StoredProjectFile(
                        "evidence-template/template.docx",
                        "evidence-template",
                        "template.docx",
                        "template.docx",
                        Files.size(templatePath),
                        "2026-04-05T00:00:00Z",
                        templatePath.toString()
                )
        ));
        return fileStorageService;
    }

    private WordExportService.EvidenceExportPayload payload() {
        Map<String, String> fieldValues = new LinkedHashMap<>();
        fieldValues.put("title", null);
        fieldValues.put("paymentDate", null);
        fieldValues.put("vendor", "2 sa");
        fieldValues.put("inspector", null);
        fieldValues.put("contractAmount", "1201019003");

        return new WordExportService.EvidenceExportPayload(
                4L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                fieldValues,
                List.of()
        );
    }

    private WordExportService.EvidenceExportPayload payloadWithItems() {
        return new WordExportService.EvidenceExportPayload(
                4L,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                Map.of(),
                List.of(
                        new WordExportService.EvidenceLineItem("A4\uC6A9\uC9C0", "80g", "2", "EA", "3500", "7000"),
                        new WordExportService.EvidenceLineItem("\uBCFC\uD39C", "0.7", "3", "EA", "1000", "3000")
                )
        );
    }

    private Path createTemplate(List<String>... rows) throws Exception {
        Path path = tempDir.resolve("inspection-template.docx");
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            int columnCount = java.util.Arrays.stream(rows).mapToInt(List::size).max().orElse(1);
            XWPFTable table = document.createTable(rows.length, columnCount);
            for (int rowIndex = 0; rowIndex < rows.length; rowIndex++) {
                for (int cellIndex = 0; cellIndex < rows[rowIndex].size(); cellIndex++) {
                    table.getRow(rowIndex).getCell(cellIndex).setText(rows[rowIndex].get(cellIndex));
                }
            }
            document.write(outputStream);
            Files.write(path, outputStream.toByteArray());
        }
        return path;
    }
}
