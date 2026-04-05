package com.adminsaas.accounting.web;

import com.adminsaas.accounting.service.FileStorageService;
import com.adminsaas.accounting.service.ProjectTemplateLoader;
import com.adminsaas.accounting.service.ReceiptOcrService;
import com.adminsaas.accounting.service.TemplateFieldService;
import com.adminsaas.accounting.template.domain.DocumentTemplate;
import com.adminsaas.accounting.template.domain.InspectionReportExportMode;
import com.adminsaas.accounting.template.domain.InspectionReportItemRow;
import com.adminsaas.accounting.template.domain.InspectionReportReceiptEntry;
import com.adminsaas.accounting.template.domain.NormalizedLabel;
import com.adminsaas.accounting.template.domain.NormalizedTable;
import com.adminsaas.accounting.template.domain.TemplateCandidateRecommendation;
import com.adminsaas.accounting.template.domain.TemplateExtractionSummary;
import com.adminsaas.accounting.template.domain.TemplateMappingSchema;
import com.adminsaas.accounting.template.service.InspectionReportGenerationService;
import com.adminsaas.accounting.template.service.InspectionReportWordExportService;
import com.adminsaas.accounting.template.service.TemplateService;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/files")
public class FileUploadController {

    private final FileStorageService fileStorageService;
    private final ReceiptOcrService receiptOcrService;
    private final TemplateFieldService templateFieldService;
    private final ProjectTemplateLoader projectTemplateLoader;
    private final TemplateService templateService;
    private final InspectionReportGenerationService inspectionReportGenerationService;
    private final InspectionReportWordExportService inspectionReportWordExportService;

    public FileUploadController(FileStorageService fileStorageService,
                                ReceiptOcrService receiptOcrService,
                                TemplateFieldService templateFieldService,
                                ProjectTemplateLoader projectTemplateLoader,
                                TemplateService templateService,
                                InspectionReportGenerationService inspectionReportGenerationService,
                                InspectionReportWordExportService inspectionReportWordExportService) {
        this.fileStorageService = fileStorageService;
        this.receiptOcrService = receiptOcrService;
        this.templateFieldService = templateFieldService;
        this.projectTemplateLoader = projectTemplateLoader;
        this.templateService = templateService;
        this.inspectionReportGenerationService = inspectionReportGenerationService;
        this.inspectionReportWordExportService = inspectionReportWordExportService;
    }

    @PostMapping(path = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public FileUploadResponse upload(@PathVariable Long projectId,
                                     @RequestParam String category,
                                     @RequestParam MultipartFile file) throws IOException {
        FileStorageService.StoredFile storedFile = fileStorageService.store(projectId, category, file);
        return new FileUploadResponse(
                storedFile.originalFilename(),
                storedFile.storedFilename(),
                storedFile.size(),
                storedFile.path()
        );
    }

    @GetMapping
    public List<ProjectFileResponse> listFiles(@PathVariable Long projectId,
                                               @RequestParam(required = false) String category) throws IOException {
        return fileStorageService.list(projectId, category).stream()
                .map(file -> new ProjectFileResponse(
                        file.relativePath(),
                        file.category(),
                        file.originalFilename(),
                        file.storedFilename(),
                        file.size(),
                        file.uploadedAt()
                ))
                .toList();
    }

    @GetMapping("/template-fields")
    public TemplateFieldAnalysisResponse inspectTemplateFields(@PathVariable Long projectId,
                                                               @RequestParam String path) throws IOException {
        TemplateFieldService.TemplateFieldAnalysis analysis = templateFieldService.analyze(projectId, path);
        return new TemplateFieldAnalysisResponse(
                analysis.originalFilename(),
                analysis.templateType(),
                analysis.supported(),
                analysis.placeholders(),
                analysis.warnings()
        );
    }

    @GetMapping("/template-extract")
    public ProjectTemplateExtractResponse extractTemplate(@PathVariable Long projectId,
                                                          @RequestParam String path) throws IOException {
        DocumentTemplate template = projectTemplateLoader.load(projectId, path);
        TemplateCandidateRecommendation recommendation = templateService.recommend(template);
        return new ProjectTemplateExtractResponse(
                recommendation.schema(),
                recommendation.detectedLabels(),
                recommendation.detectedTables(),
                recommendation.summary()
        );
    }

    @PostMapping("/inspection-report/generate")
    public InspectionReportGenerateResponse generateInspectionReport(@PathVariable Long projectId,
                                                                    @RequestParam String path,
                                                                    @org.springframework.web.bind.annotation.RequestBody InspectionReportGenerateRequest request) throws IOException {
        DocumentTemplate template = projectTemplateLoader.load(projectId, path);
        var job = inspectionReportGenerationService.generate(
                template,
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

    @PostMapping(path = "/inspection-report/export-word")
    public ResponseEntity<ByteArrayResource> exportInspectionReportWord(@PathVariable Long projectId,
                                                                       @RequestParam String path,
                                                                       @org.springframework.web.bind.annotation.RequestBody InspectionReportGenerateRequest request) throws IOException {
        DocumentTemplate template = projectTemplateLoader.load(projectId, path);
        InspectionReportWordExportService.ExportedInspectionReport exported = inspectionReportWordExportService.export(
                projectId,
                path,
                template,
                request.mode(),
                request.entries() == null ? List.of() : request.entries().stream().map(ReceiptEntryRequest::toDomain).toList()
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + exported.filename() + "\"")
                .contentType(MediaType.parseMediaType(exported.contentType()))
                .body(new ByteArrayResource(exported.content()));
    }

    @GetMapping("/download")
    public ResponseEntity<ByteArrayResource> downloadFile(@PathVariable Long projectId,
                                                          @RequestParam String path) throws IOException {
        FileStorageService.ResourceFile file = fileStorageService.load(projectId, path);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + file.filename() + "\"")
                .contentType(MediaType.parseMediaType(file.contentType() == null ? MediaType.APPLICATION_OCTET_STREAM_VALUE : file.contentType()))
                .body(new ByteArrayResource(file.content()));
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteFile(@PathVariable Long projectId, @RequestParam String path) throws IOException {
        fileStorageService.delete(projectId, path);
    }

    @PostMapping(path = "/receipt-ocr", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ReceiptOcrResponse uploadReceiptAndExtract(@PathVariable Long projectId,
                                                      @RequestParam MultipartFile file) throws IOException {
        ReceiptOcrService.ReceiptOcrResult result = receiptOcrService.analyze(projectId, file);
        return new ReceiptOcrResponse(
                result.originalFilename(),
                result.storedFilename(),
                result.size(),
                result.path(),
                result.ocrAvailable(),
                result.status(),
                result.rawText(),
                result.confidence(),
                new ParsedReceiptFieldsResponse(
                        result.fields().paymentDate(),
                        result.fields().vendor(),
                        result.fields().itemName(),
                        result.fields().quantity(),
                        result.fields().unitPrice(),
                        result.fields().amount(),
                        result.fields().paymentMethod(),
                        result.fields().category(),
                        result.fields().subcategory(),
                        result.fields().lineItems().stream()
                                .map(lineItem -> new ReceiptOcrLineItemResponse(
                                        lineItem.itemName(),
                                        lineItem.spec(),
                                        lineItem.quantity(),
                                        lineItem.unit(),
                                        lineItem.unitPrice(),
                                        lineItem.amount()
                                ))
                                .toList()
                ),
                result.warnings()
        );
    }

    public record FileUploadResponse(
            String originalFilename,
            String storedFilename,
            long size,
            String path
    ) {
    }

    public record ProjectFileResponse(
            String relativePath,
            String category,
            String originalFilename,
            String storedFilename,
            long size,
            String uploadedAt
    ) {
    }

    public record ReceiptOcrResponse(
            String originalFilename,
            String storedFilename,
            long size,
            String path,
            boolean ocrAvailable,
            String status,
            String rawText,
            Double confidence,
            ParsedReceiptFieldsResponse fields,
            java.util.List<String> warnings
    ) {
    }

    public record ParsedReceiptFieldsResponse(
            java.time.LocalDate paymentDate,
            String vendor,
            String itemName,
            java.math.BigDecimal quantity,
            java.math.BigDecimal unitPrice,
            java.math.BigDecimal amount,
            String paymentMethod,
            String category,
            String subcategory,
            java.util.List<ReceiptOcrLineItemResponse> lineItems
    ) {
    }

    public record ReceiptOcrLineItemResponse(
            String itemName,
            String spec,
            java.math.BigDecimal quantity,
            String unit,
            java.math.BigDecimal unitPrice,
            java.math.BigDecimal amount
    ) {
    }

    public record TemplateFieldAnalysisResponse(
            String originalFilename,
            String templateType,
            boolean supported,
            List<String> placeholders,
            List<String> warnings
    ) {
    }

    public record ProjectTemplateExtractResponse(
            TemplateMappingSchema schema,
            List<NormalizedLabel> detectedLabels,
            List<NormalizedTable> detectedTables,
            TemplateExtractionSummary summary
    ) {
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
