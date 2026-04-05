package com.adminsaas.accounting.template.service;

import com.adminsaas.accounting.service.FileStorageService;
import com.adminsaas.accounting.template.domain.DocumentTemplate;
import com.adminsaas.accounting.template.domain.InspectionReportDocumentHead;
import com.adminsaas.accounting.template.domain.InspectionReportExportGroup;
import com.adminsaas.accounting.template.domain.InspectionReportExportJob;
import com.adminsaas.accounting.template.domain.InspectionReportExportMode;
import com.adminsaas.accounting.template.domain.InspectionReportGeneratedDocument;
import com.adminsaas.accounting.template.domain.InspectionReportItemRow;
import com.adminsaas.accounting.template.domain.InspectionReportReceiptBlock;
import com.adminsaas.accounting.template.domain.InspectionReportReceiptEntry;
import com.adminsaas.accounting.template.domain.NormalizedTemplateContent;
import com.adminsaas.accounting.template.domain.TemplateDocumentType;
import com.adminsaas.accounting.template.domain.TemplateExtractionSummary;
import com.adminsaas.accounting.template.domain.TemplateMappingSchema;
import com.adminsaas.accounting.template.domain.TemplateStatus;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class InspectionReportWordExportServiceTest {

    @Test
    void exportsOneHeadAndRepeatedReceiptBlocksForGroupedDocument() throws Exception {
        InspectionReportGenerationService generationService = mock(InspectionReportGenerationService.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        byte[] templateBytes = createTemplateBytes();

        when(generationService.generate(any(DocumentTemplate.class), eq(InspectionReportExportMode.BY_DATE), any()))
                .thenReturn(new InspectionReportExportJob(
                        "template-1",
                        InspectionReportExportMode.BY_DATE,
                        List.of(new InspectionReportExportGroup("2026-04-05", "2026-04-05", List.of())),
                        List.of(new InspectionReportGeneratedDocument(
                                "2026-04-05",
                                "물품검수조서_2026-04-05",
                                new InspectionReportDocumentHead("물품검수조서"),
                                List.of(
                                        block("건명 A", "2026-04-05", "거래처 A", "검수자 A", "1000"),
                                        block("건명 B", "2026-04-05", "거래처 B", "검수자 B", "2000")
                                )
                        ))
                ));
        when(fileStorageService.load(4L, "inspection-template/report.docx"))
                .thenReturn(new FileStorageService.ResourceFile("report.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", templateBytes));

        InspectionReportWordExportService service = new InspectionReportWordExportService(generationService, fileStorageService);

        InspectionReportWordExportService.ExportedInspectionReport exported = service.export(
                4L,
                "inspection-template/report.docx",
                template(),
                InspectionReportExportMode.BY_DATE,
                List.of(new InspectionReportReceiptEntry("1", null, null, null, null, null, null, 0, List.of()))
        );

        assertEquals("application/vnd.openxmlformats-officedocument.wordprocessingml.document", exported.contentType());
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(exported.content()))) {
            long titleCount = document.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .filter("물품검수조서"::equals)
                    .count();
            assertEquals(1, titleCount);

            List<XWPFTable> tables = document.getTables();
            assertEquals(4, tables.size());
            assertEquals("건명", tables.get(0).getRow(0).getCell(0).getText());
            assertEquals("건명 A", tables.get(0).getRow(0).getCell(1).getText());
            assertEquals("품명", tables.get(1).getRow(0).getCell(0).getText());
            assertEquals("연필", tables.get(1).getRow(1).getCell(0).getText());
            assertEquals("건명 B", tables.get(2).getRow(0).getCell(1).getText());
            assertEquals("2000", tables.get(2).getRow(2).getCell(1).getText());
        }
    }

    @Test
    void rejectsNonDocxTemplateForGroupedExport() {
        InspectionReportGenerationService generationService = mock(InspectionReportGenerationService.class);
        FileStorageService fileStorageService = mock(FileStorageService.class);
        when(generationService.generate(any(DocumentTemplate.class), eq(InspectionReportExportMode.ALL), any()))
                .thenReturn(new InspectionReportExportJob(
                        "template-1",
                        InspectionReportExportMode.ALL,
                        List.of(),
                        List.of(new InspectionReportGeneratedDocument(
                                "all",
                                "물품검수조서_전체",
                                new InspectionReportDocumentHead("물품검수조서"),
                                List.of(block("건명", "2026-04-05", "거래처", "검수자", "1000"))
                        ))
                ));

        InspectionReportWordExportService service = new InspectionReportWordExportService(generationService, fileStorageService);

        ResponseStatusException exception = assertThrows(ResponseStatusException.class, () -> service.export(
                4L,
                "inspection-template/report.hwp",
                template(),
                InspectionReportExportMode.ALL,
                List.of()
        ));

        assertEquals("400 BAD_REQUEST \"날짜별 저장과 전체 저장은 DOCX 물품검수조서 템플릿에서만 지원됩니다.\"", exception.getMessage());
    }

    private DocumentTemplate template() {
        return new DocumentTemplate(
                "template-1",
                "report.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                0L,
                TemplateDocumentType.INSPECTION_REPORT,
                TemplateStatus.MAPPED,
                OffsetDateTime.now(),
                new NormalizedTemplateContent("inspection", List.of(), List.of(), List.of()),
                new TemplateExtractionSummary(0, 0, 0, 0),
                new TemplateMappingSchema("template-1", TemplateDocumentType.INSPECTION_REPORT, List.of(), List.of())
        );
    }

    private InspectionReportReceiptBlock block(String title,
                                               String paymentDate,
                                               String vendor,
                                               String inspector,
                                               String amount) {
        return new InspectionReportReceiptBlock(
                title,
                title,
                paymentDate,
                paymentDate,
                vendor,
                inspector,
                amount,
                null,
                0,
                List.of(new InspectionReportItemRow("연필", "HB", "2", "EA", "500", "1000"))
        );
    }

    private byte[] createTemplateBytes() throws Exception {
        try (XWPFDocument document = new XWPFDocument();
             ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            XWPFParagraph title = document.createParagraph();
            title.createRun().setText("물품검수조서");

            XWPFTable metadata = document.createTable(3, 4);
            metadata.getRow(0).getCell(0).setText("건명");
            metadata.getRow(0).getCell(1).setText("-");
            metadata.getRow(0).getCell(2).setText("결제일자");
            metadata.getRow(0).getCell(3).setText("-");
            metadata.getRow(1).getCell(0).setText("납품처");
            metadata.getRow(1).getCell(1).setText("-");
            metadata.getRow(1).getCell(2).setText("검수자");
            metadata.getRow(1).getCell(3).setText("-");
            metadata.getRow(2).getCell(0).setText("계약금액");
            metadata.getRow(2).getCell(1).setText("-");
            metadata.getRow(2).getCell(2).setText("-");
            metadata.getRow(2).getCell(3).setText("-");

            XWPFTable items = document.createTable(2, 6);
            items.getRow(0).getCell(0).setText("품명");
            items.getRow(0).getCell(1).setText("규격");
            items.getRow(0).getCell(2).setText("수량");
            items.getRow(0).getCell(3).setText("단위");
            items.getRow(0).getCell(4).setText("단가");
            items.getRow(0).getCell(5).setText("금액");
            for (int index = 0; index < 6; index++) {
                items.getRow(1).getCell(index).setText("-");
            }

            document.write(outputStream);
            return outputStream.toByteArray();
        }
    }
}
