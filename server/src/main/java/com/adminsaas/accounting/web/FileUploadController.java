package com.adminsaas.accounting.web;

import com.adminsaas.accounting.service.FileStorageService;
import com.adminsaas.accounting.service.ReceiptOcrService;
import com.adminsaas.accounting.service.TemplateFieldService;
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

    public FileUploadController(FileStorageService fileStorageService,
                                ReceiptOcrService receiptOcrService,
                                TemplateFieldService templateFieldService) {
        this.fileStorageService = fileStorageService;
        this.receiptOcrService = receiptOcrService;
        this.templateFieldService = templateFieldService;
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
                        result.fields().subcategory()
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
            String subcategory
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
}
