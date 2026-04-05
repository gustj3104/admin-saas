package com.adminsaas.accounting.web;

import com.adminsaas.accounting.domain.DocumentStatus;
import com.adminsaas.accounting.domain.EvidenceDocumentEntity;
import com.adminsaas.accounting.domain.ExpenseEntity;
import com.adminsaas.accounting.service.EvidenceDocumentService;
import com.adminsaas.accounting.service.ExpenseService;
import com.adminsaas.accounting.service.HwpExportService;
import com.adminsaas.accounting.service.NativeHwpExportService;
import com.adminsaas.accounting.service.ProjectService;
import com.adminsaas.accounting.service.WordExportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/documents")
public class EvidenceDocumentController {

    private final EvidenceDocumentService evidenceDocumentService;
    private final WordExportService wordExportService;
    private final NativeHwpExportService nativeHwpExportService;
    private final ProjectService projectService;
    private final ExpenseService expenseService;

    public EvidenceDocumentController(EvidenceDocumentService evidenceDocumentService,
                                      WordExportService wordExportService,
                                      NativeHwpExportService nativeHwpExportService,
                                      ProjectService projectService,
                                      ExpenseService expenseService) {
        this.evidenceDocumentService = evidenceDocumentService;
        this.wordExportService = wordExportService;
        this.nativeHwpExportService = nativeHwpExportService;
        this.projectService = projectService;
        this.expenseService = expenseService;
    }

    @GetMapping
    public List<DocumentResponse> getDocuments(@RequestParam Long projectId) {
        return evidenceDocumentService.findByProjectId(projectId).stream().map(DocumentResponse::from).toList();
    }

    @GetMapping("/{id}")
    public DocumentResponse getDocument(@PathVariable Long id) {
        return DocumentResponse.from(evidenceDocumentService.findById(id));
    }

    @PostMapping
    public DocumentResponse createDocument(@Valid @RequestBody CreateDocumentRequest request) {
        ExpenseEntity expense = expenseService.findById(request.expenseId());
        EvidenceDocumentEntity document = new EvidenceDocumentEntity();
        document.setProject(projectService.findById(request.projectId()));
        document.setExpense(expense);
        document.setDocumentType(request.documentType());
        document.setVendor(request.vendor());
        document.setAmount(request.amount());
        document.setCreatedDate(request.createdDate());
        document.setStatus(request.status());
        return DocumentResponse.from(evidenceDocumentService.save(document));
    }

    @PostMapping(path = "/export/word", produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<byte[]> exportWord(@Valid @RequestBody EvidenceExportRequest request) throws IOException {
        byte[] document = wordExportService.createEvidenceDocument(toWordPayload(request));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"evidence-document.docx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(document);
    }

    @PostMapping(path = "/export/hwp", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> exportHwp(@Valid @RequestBody EvidenceExportRequest request) throws IOException {
        byte[] document = nativeHwpExportService.createEvidenceDocument(
                toWordPayload(request),
                toHwpPayload(request)
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"evidence-document.hwp\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(document);
    }

    @PostMapping(path = "/preview/word")
    public PreviewResponse previewWord(@Valid @RequestBody EvidenceExportRequest request) throws IOException {
        WordExportService.EvidencePreviewResult preview = wordExportService.createEvidencePreview(toWordPayload(request));
        return new PreviewResponse(preview.paragraphs(), preview.tables());
    }

    private WordExportService.EvidenceExportPayload toWordPayload(EvidenceExportRequest request) {
        return new WordExportService.EvidenceExportPayload(
                request.projectId(),
                request.templateCategory(),
                request.projectName(),
                request.paymentDate(),
                request.vendor(),
                request.itemName(),
                request.category(),
                request.subcategory(),
                request.paymentMethod(),
                request.amount(),
                request.notes(),
                request.fieldValues(),
                request.lineItems() == null ? List.of() : request.lineItems().stream()
                        .map(item -> new WordExportService.EvidenceLineItem(
                                item.itemName(),
                                item.spec(),
                                item.quantity(),
                                item.unit(),
                                item.unitPrice(),
                                item.amount()
                        ))
                        .toList()
        );
    }

    private HwpExportService.HwpEvidencePayload toHwpPayload(EvidenceExportRequest request) {
        return new HwpExportService.HwpEvidencePayload(
                request.projectName(),
                request.paymentDate(),
                request.vendor(),
                request.itemName(),
                request.category(),
                request.subcategory(),
                request.paymentMethod(),
                request.amount(),
                request.notes(),
                request.fieldValues()
        );
    }

    public record DocumentResponse(
            Long id,
            String expenseCode,
            String documentType,
            String vendor,
            BigDecimal amount,
            LocalDate createdDate,
            DocumentStatus status
    ) {
        static DocumentResponse from(EvidenceDocumentEntity entity) {
            return new DocumentResponse(
                    entity.getId(),
                    entity.getExpense().getExpenseCode(),
                    entity.getDocumentType(),
                    entity.getVendor(),
                    entity.getAmount(),
                    entity.getCreatedDate(),
                    entity.getStatus()
            );
        }
    }

    public record EvidenceExportRequest(
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
            List<EvidenceLineItemRequest> lineItems
    ) {
    }

    public record EvidenceLineItemRequest(
            String itemName,
            String spec,
            String quantity,
            String unit,
            String unitPrice,
            String amount
    ) {
    }

    public record CreateDocumentRequest(
            Long projectId,
            Long expenseId,
            String documentType,
            String vendor,
            BigDecimal amount,
            LocalDate createdDate,
            DocumentStatus status
    ) {
    }

    public record PreviewResponse(
            List<String> paragraphs,
            List<List<List<String>>> tables
    ) {
    }
}
