package com.adminsaas.accounting.template.service;

import com.adminsaas.accounting.service.ProjectTemplateLoader;
import com.adminsaas.accounting.template.domain.DocumentTemplate;
import com.adminsaas.accounting.template.domain.InspectionReportExportJob;
import com.adminsaas.accounting.template.domain.InspectionReportExportMode;
import com.adminsaas.accounting.template.domain.InspectionReportItemRow;
import com.adminsaas.accounting.template.domain.InspectionReportReceiptEntry;
import com.adminsaas.accounting.template.domain.NormalizedBlock;
import com.adminsaas.accounting.template.domain.NormalizedTemplateContent;
import com.adminsaas.accounting.template.domain.TemplateDocumentType;
import com.adminsaas.accounting.template.domain.TemplateExtractionSummary;
import com.adminsaas.accounting.template.domain.TemplateMappingSchema;
import com.adminsaas.accounting.template.domain.TemplateStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InspectionReportGenerationServiceTest {

    private final InspectionReportGroupingService groupingService = new InspectionReportGroupingService();
    private final InspectionReportDocumentAssembler documentAssembler = new InspectionReportDocumentAssembler(new InspectionReportBlockRenderer());

    @Test
    void generatesOneDocumentPerReceiptInPerReceiptMode() {
        InspectionReportGenerationService service = new InspectionReportGenerationService(
                mockTemplateService(),
                mock(ProjectTemplateLoader.class),
                groupingService,
                documentAssembler
        );

        InspectionReportExportJob job = service.generate("template-1", InspectionReportExportMode.PER_RECEIPT, List.of(
                entry("A", "2025-09-25", 0),
                entry("B", "2025-09-26", 1)
        ));

        assertEquals(2, job.documents().size());
        assertEquals(1, job.documents().get(0).receiptBlocks().size());
        assertEquals(1, job.documents().get(1).receiptBlocks().size());
        assertEquals("물품검수조서", job.documents().get(0).head().title());
    }

    @Test
    void groupsByNormalizedDateInByDateMode() {
        InspectionReportGenerationService service = new InspectionReportGenerationService(
                mockTemplateService(),
                mock(ProjectTemplateLoader.class),
                groupingService,
                documentAssembler
        );

        InspectionReportExportJob job = service.generate("template-1", InspectionReportExportMode.BY_DATE, List.of(
                entry("A", "2025-09-25", 0),
                entry("B", "2025.09.25", 1),
                entry("C", "2025-09-26", 2)
        ));

        assertEquals(2, job.documents().size());
        assertEquals(2, job.documents().get(0).receiptBlocks().size());
        assertEquals(1, job.documents().get(1).receiptBlocks().size());
        assertEquals("물품검수조서", job.documents().get(0).head().title());
    }

    @Test
    void generatesSingleDocumentInAllModeWithoutDuplicatingHead() {
        InspectionReportGenerationService service = new InspectionReportGenerationService(
                mockTemplateService(),
                mock(ProjectTemplateLoader.class),
                groupingService,
                documentAssembler
        );

        InspectionReportExportJob job = service.generate("template-1", InspectionReportExportMode.ALL, List.of(
                entry("A", "2025-09-25", 0),
                entry("B", "2025-09-25", 1),
                entry("C", "2025-09-26", 2)
        ));

        assertEquals(1, job.documents().size());
        assertEquals(3, job.documents().get(0).receiptBlocks().size());
        assertEquals("물품검수조서_전체", job.documents().get(0).title());
    }

    @Test
    void groupsMissingDatesIntoUnknownBucket() {
        InspectionReportGenerationService service = new InspectionReportGenerationService(
                mockTemplateService(),
                mock(ProjectTemplateLoader.class),
                groupingService,
                documentAssembler
        );

        InspectionReportExportJob job = service.generate("template-1", InspectionReportExportMode.BY_DATE, List.of(
                entry("A", null, 0),
                entry("B", "2025-09-25", 1)
        ));

        assertEquals(2, job.documents().size());
        assertEquals("2025-09-25", job.documents().get(0).groupKey());
        assertEquals("unknown-date", job.documents().get(1).groupKey());
    }

    @Test
    void loadsProjectTemplateWhenProjectTemplateIdIsProvided() throws Exception {
        TemplateService templateService = mock(TemplateService.class);
        ProjectTemplateLoader projectTemplateLoader = mock(ProjectTemplateLoader.class);
        when(projectTemplateLoader.load(4L, "evidence-template/inspection.docx"))
                .thenReturn(inspectionTemplate("project-4:evidence-template/inspection.docx"));

        InspectionReportGenerationService service = new InspectionReportGenerationService(
                templateService,
                projectTemplateLoader,
                groupingService,
                documentAssembler
        );

        InspectionReportExportJob job = service.generate(
                "project-4:evidence-template/inspection.docx",
                InspectionReportExportMode.PER_RECEIPT,
                List.of(entry("A", "2025-09-25", 0))
        );

        assertEquals(1, job.documents().size());
        verify(projectTemplateLoader).load(4L, "evidence-template/inspection.docx");
    }

    private TemplateService mockTemplateService() {
        TemplateService templateService = mock(TemplateService.class);
        when(templateService.getTemplate("template-1")).thenReturn(inspectionTemplate("template-1"));
        return templateService;
    }

    private DocumentTemplate inspectionTemplate(String templateId) {
        return new DocumentTemplate(
                templateId,
                "inspection.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                0L,
                TemplateDocumentType.INSPECTION_REPORT,
                TemplateStatus.MAPPED,
                OffsetDateTime.now(),
                new NormalizedTemplateContent(
                        "inspection_report",
                        List.of(new NormalizedBlock("heading", "물품검수조서", List.of())),
                        List.of(),
                        List.of()
                ),
                new TemplateExtractionSummary(0, 0, 0, 0),
                new TemplateMappingSchema(templateId, TemplateDocumentType.INSPECTION_REPORT, List.of(), List.of())
        );
    }

    private InspectionReportReceiptEntry entry(String id, String paymentDate, int order) {
        return new InspectionReportReceiptEntry(
                id,
                "건명-" + id,
                paymentDate,
                "납품처-" + id,
                "검수자-" + id,
                "1000",
                null,
                order,
                List.of(new InspectionReportItemRow("품목-" + id, null, "1", "EA", "1000", "1000"))
        );
    }
}
