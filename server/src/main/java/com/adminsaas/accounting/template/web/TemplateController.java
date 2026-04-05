package com.adminsaas.accounting.template.web;

import com.adminsaas.accounting.template.domain.DocumentTemplate;
import com.adminsaas.accounting.template.domain.InspectionReportExportMode;
import com.adminsaas.accounting.template.domain.InspectionReportItemRow;
import com.adminsaas.accounting.template.domain.InspectionReportReceiptEntry;
import com.adminsaas.accounting.template.domain.NormalizedLabel;
import com.adminsaas.accounting.template.domain.NormalizedTable;
import com.adminsaas.accounting.template.domain.NormalizedTemplateContent;
import com.adminsaas.accounting.template.domain.TemplateCandidateRecommendation;
import com.adminsaas.accounting.template.domain.TemplateDocumentType;
import com.adminsaas.accounting.template.domain.TemplateExtractionSummary;
import com.adminsaas.accounting.template.domain.TemplateFieldMapping;
import com.adminsaas.accounting.template.domain.TemplateFieldType;
import com.adminsaas.accounting.template.domain.TemplateMappingSchema;
import com.adminsaas.accounting.template.domain.TemplateSourceType;
import com.adminsaas.accounting.template.domain.TemplateStatus;
import com.adminsaas.accounting.template.domain.TemplateTableColumnMapping;
import com.adminsaas.accounting.template.domain.TemplateTableMapping;
import com.adminsaas.accounting.template.service.InspectionReportGenerationService;
import com.adminsaas.accounting.template.service.TemplateService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/templates")
public class TemplateController {

    private final TemplateService templateService;
    private final InspectionReportGenerationService inspectionReportGenerationService;

    public TemplateController(TemplateService templateService,
                              InspectionReportGenerationService inspectionReportGenerationService) {
        this.templateService = templateService;
        this.inspectionReportGenerationService = inspectionReportGenerationService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UploadTemplateResponse uploadMultipart(
            @RequestParam MultipartFile file,
            @RequestParam(required = false) String structuredContent
    ) throws IOException {
        DocumentTemplate template = templateService.upload(file, structuredContent);
        return UploadTemplateResponse.from(template);
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public UploadTemplateResponse uploadJson(@Valid @RequestBody UploadTemplateJsonRequest request) {
        DocumentTemplate template = templateService.uploadJson(request.filename(), request.contentType(), request.structuredContent());
        return UploadTemplateResponse.from(template);
    }

    @PostMapping("/{templateId}/extract")
    public ExtractTemplateResponse extract(@PathVariable String templateId) {
        TemplateCandidateRecommendation recommendation = templateService.extract(templateId);
        return new ExtractTemplateResponse(
                MappingSchemaResponse.from(recommendation.schema()),
                recommendation.detectedLabels(),
                recommendation.detectedTables(),
                recommendation.summary()
        );
    }

    @PutMapping("/{templateId}/mappings")
    public MappingSchemaResponse updateMappings(@PathVariable String templateId, @Valid @RequestBody MappingSchemaRequest request) {
        TemplateMappingSchema saved = templateService.updateMappings(templateId, request.toDomain(templateId));
        return MappingSchemaResponse.from(saved);
    }

    @GetMapping("/{templateId}/mappings")
    public MappingSchemaResponse getMappings(@PathVariable String templateId) {
        return MappingSchemaResponse.from(templateService.getMappings(templateId));
    }

    @GetMapping("/{templateId}")
    public TemplateDetailResponse getTemplate(@PathVariable String templateId) {
        return TemplateDetailResponse.from(templateService.getTemplate(templateId));
    }

    @PostMapping("/{templateId}/generate")
    public InspectionReportGenerateResponse generate(@PathVariable String templateId,
                                                     @Valid @RequestBody InspectionReportGenerateRequest request) {
        var job = inspectionReportGenerationService.generate(
                templateId,
                request.mode(),
                request.entries() == null ? List.of() : request.entries().stream().map(ReceiptEntryRequest::toDomain).toList()
        );
        return new InspectionReportGenerateResponse(
                job.mode(),
                job.documents().size(),
                job.documents().stream()
                        .map(document -> new GeneratedDocumentResponse(
                                document.groupKey(),
                                document.title(),
                                document.receiptBlocks().size()
                        ))
                        .toList()
        );
    }

    public record UploadTemplateJsonRequest(
            @NotBlank String filename,
            @NotBlank String contentType,
            @Valid NormalizedTemplateContent structuredContent
    ) {
    }

    public record UploadTemplateResponse(
            String templateId,
            String filename,
            String contentType,
            long size,
            TemplateDocumentType detectedDocumentType,
            TemplateStatus status,
            OffsetDateTime uploadedAt
    ) {
        private static UploadTemplateResponse from(DocumentTemplate template) {
            return new UploadTemplateResponse(
                    template.id(),
                    template.filename(),
                    template.contentType(),
                    template.size(),
                    template.documentType(),
                    template.status(),
                    template.uploadedAt()
            );
        }
    }

    public record ExtractTemplateResponse(
            MappingSchemaResponse schema,
            List<NormalizedLabel> detectedLabels,
            List<NormalizedTable> detectedTables,
            TemplateExtractionSummary summary
    ) {
    }

    public record TemplateDetailResponse(
            String templateId,
            String filename,
            String contentType,
            long size,
            TemplateDocumentType detectedDocumentType,
            TemplateStatus status,
            OffsetDateTime uploadedAt,
            TemplateExtractionSummary extractionSummary
    ) {
        private static TemplateDetailResponse from(DocumentTemplate template) {
            return new TemplateDetailResponse(
                    template.id(),
                    template.filename(),
                    template.contentType(),
                    template.size(),
                    template.documentType(),
                    template.status(),
                    template.uploadedAt(),
                    template.extractionSummary()
            );
        }
    }

    public record MappingSchemaRequest(
            TemplateDocumentType documentType,
            List<FieldMappingRequest> fields,
            List<TableMappingRequest> tables
    ) {
        private TemplateMappingSchema toDomain(String templateId) {
            return new TemplateMappingSchema(
                    templateId,
                    documentType,
                    fields == null ? List.of() : fields.stream().map(FieldMappingRequest::toDomain).toList(),
                    tables == null ? List.of() : tables.stream().map(TableMappingRequest::toDomain).toList()
            );
        }
    }

    public record FieldMappingRequest(
            String id,
            String key,
            String displayName,
            String description,
            TemplateSourceType sourceType,
            String sourceName,
            TemplateFieldType fieldType,
            boolean recommended,
            boolean confirmed,
            boolean createdBySystem
    ) {
        private TemplateFieldMapping toDomain() {
            return new TemplateFieldMapping(id, key, displayName, description, sourceType, sourceName, fieldType, recommended, confirmed, createdBySystem);
        }
    }

    public record TableMappingRequest(
            String id,
            String key,
            String displayName,
            String sourceName,
            boolean recommended,
            boolean confirmed,
            boolean createdBySystem,
            List<TableColumnMappingRequest> columns
    ) {
        private TemplateTableMapping toDomain() {
            return new TemplateTableMapping(
                    id,
                    key,
                    displayName,
                    sourceName,
                    recommended,
                    confirmed,
                    createdBySystem,
                    columns == null ? List.of() : columns.stream().map(TableColumnMappingRequest::toDomain).toList()
            );
        }
    }

    public record TableColumnMappingRequest(
            String id,
            String key,
            String displayName,
            String description,
            String sourceName,
            TemplateFieldType fieldType,
            boolean recommended,
            boolean confirmed,
            boolean createdBySystem
    ) {
        private TemplateTableColumnMapping toDomain() {
            return new TemplateTableColumnMapping(id, key, displayName, description, sourceName, fieldType, recommended, confirmed, createdBySystem);
        }
    }

    public record MappingSchemaResponse(
            String templateId,
            TemplateDocumentType documentType,
            List<TemplateFieldMapping> fields,
            List<TemplateTableMapping> tables
    ) {
        private static MappingSchemaResponse from(TemplateMappingSchema schema) {
            return new MappingSchemaResponse(schema.templateId(), schema.documentType(), schema.fields(), schema.tables());
        }
    }

    public record InspectionReportGenerateRequest(
            InspectionReportExportMode mode,
            List<ReceiptEntryRequest> entries
    ) {
    }

    public record ReceiptEntryRequest(
            String receiptId,
            String title,
            String paymentDate,
            String vendor,
            String inspector,
            String contractAmount,
            String receiptImagePath,
            Integer receiptOrder,
            List<ItemRowRequest> itemRows
    ) {
        private InspectionReportReceiptEntry toDomain() {
            return new InspectionReportReceiptEntry(
                    receiptId,
                    title,
                    paymentDate,
                    vendor,
                    inspector,
                    contractAmount,
                    receiptImagePath,
                    receiptOrder == null ? 0 : receiptOrder,
                    itemRows == null ? List.of() : itemRows.stream().map(ItemRowRequest::toDomain).toList()
            );
        }
    }

    public record ItemRowRequest(
            String itemName,
            String spec,
            String quantity,
            String unit,
            String unitPrice,
            String amount
    ) {
        private InspectionReportItemRow toDomain() {
            return new InspectionReportItemRow(itemName, spec, quantity, unit, unitPrice, amount);
        }
    }

    public record InspectionReportGenerateResponse(
            InspectionReportExportMode mode,
            int documentCount,
            List<GeneratedDocumentResponse> documents
    ) {
    }

    public record GeneratedDocumentResponse(
            String groupKey,
            String title,
            int blockCount
    ) {
    }
}
