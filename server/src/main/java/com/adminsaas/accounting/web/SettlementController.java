package com.adminsaas.accounting.web;

import com.adminsaas.accounting.domain.SettlementReportEntity;
import com.adminsaas.accounting.service.HwpExportService;
import com.adminsaas.accounting.service.NativeHwpExportService;
import com.adminsaas.accounting.service.ProjectService;
import com.adminsaas.accounting.service.SettlementService;
import com.adminsaas.accounting.service.WordExportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/settlements")
public class SettlementController {

    private final SettlementService settlementService;
    private final ProjectService projectService;
    private final WordExportService wordExportService;
    private final NativeHwpExportService nativeHwpExportService;

    public SettlementController(SettlementService settlementService,
                                ProjectService projectService,
                                WordExportService wordExportService,
                                NativeHwpExportService nativeHwpExportService) {
        this.settlementService = settlementService;
        this.projectService = projectService;
        this.wordExportService = wordExportService;
        this.nativeHwpExportService = nativeHwpExportService;
    }

    @GetMapping("/latest")
    public SettlementResponse latest(@RequestParam Long projectId) {
        return SettlementResponse.from(settlementService.latest(projectId));
    }

    @PostMapping("/generate")
    public SettlementResponse generate(@RequestParam Long projectId) {
        return SettlementResponse.from(settlementService.generate(projectService.findById(projectId)));
    }

    @PutMapping("/{id}")
    public SettlementResponse update(@PathVariable Long id, @Valid @RequestBody UpdateSettlementRequest request) {
        SettlementReportEntity report = new SettlementReportEntity();
        report.setReportTitle(request.reportTitle());
        report.setReportDate(request.reportDate());
        report.setPreparedBy(request.preparedBy());
        report.setApprovedBy(request.approvedBy());
        report.setSummaryNotes(request.summaryNotes());
        report.setTotalAllocated(request.totalAllocated());
        report.setTotalSpent(request.totalSpent());
        report.setTotalVariance(request.totalVariance());
        report.setExecutionRate(request.executionRate());
        return SettlementResponse.from(settlementService.update(id, report));
    }

    @PostMapping(path = "/export/word", produces = "application/vnd.openxmlformats-officedocument.wordprocessingml.document")
    public ResponseEntity<byte[]> exportWord(@Valid @RequestBody SettlementExportRequest request) throws IOException {
        byte[] document = wordExportService.createSettlementDocument(toWordPayload(request));

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"settlement-report.docx\"")
                .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(document);
    }

    @PostMapping(path = "/export/hwp", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> exportHwp(@Valid @RequestBody SettlementExportRequest request) throws IOException {
        byte[] document = nativeHwpExportService.createSettlementDocument(
                toWordPayload(request),
                toHwpPayload(request)
        );

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"settlement-report.hwp\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(document);
    }

    private WordExportService.SettlementExportPayload toWordPayload(SettlementExportRequest request) {
        return new WordExportService.SettlementExportPayload(
                request.reportTitle(),
                request.projectName(),
                request.reportDate(),
                request.projectPeriod(),
                request.preparedBy(),
                request.approvedBy(),
                request.summaryNotes(),
                request.totalAllocated(),
                request.totalSpent(),
                request.totalVariance(),
                request.executionRate(),
                request.items().stream()
                        .map(item -> new WordExportService.SettlementExportItem(
                                item.category(),
                                item.allocated(),
                                item.spent(),
                                item.variance(),
                                item.executionRate()
                        ))
                        .toList()
        );
    }

    private HwpExportService.HwpSettlementPayload toHwpPayload(SettlementExportRequest request) {
        return new HwpExportService.HwpSettlementPayload(
                request.reportTitle(),
                request.projectName(),
                request.reportDate(),
                request.projectPeriod(),
                request.preparedBy(),
                request.approvedBy(),
                request.summaryNotes(),
                request.totalAllocated(),
                request.totalSpent(),
                request.totalVariance(),
                request.executionRate(),
                request.items().stream()
                        .map(item -> new HwpExportService.HwpSettlementItem(
                                item.category(),
                                item.allocated(),
                                item.spent(),
                                item.variance(),
                                item.executionRate()
                        ))
                        .toList()
        );
    }

    public record SettlementResponse(
            Long id,
            Long projectId,
            String reportTitle,
            LocalDate reportDate,
            String preparedBy,
            String approvedBy,
            String summaryNotes,
            BigDecimal totalAllocated,
            BigDecimal totalSpent,
            BigDecimal totalVariance,
            BigDecimal executionRate
    ) {
        static SettlementResponse from(SettlementReportEntity entity) {
            return new SettlementResponse(
                    entity.getId(),
                    entity.getProject().getId(),
                    entity.getReportTitle(),
                    entity.getReportDate(),
                    entity.getPreparedBy(),
                    entity.getApprovedBy(),
                    entity.getSummaryNotes(),
                    entity.getTotalAllocated(),
                    entity.getTotalSpent(),
                    entity.getTotalVariance(),
                    entity.getExecutionRate()
            );
        }
    }

    public record SettlementExportRequest(
            String reportTitle,
            String projectName,
            String reportDate,
            String projectPeriod,
            String preparedBy,
            String approvedBy,
            String summaryNotes,
            String totalAllocated,
            String totalSpent,
            String totalVariance,
            String executionRate,
            List<SettlementExportItemRequest> items
    ) {
    }

    public record SettlementExportItemRequest(
            String category,
            String allocated,
            String spent,
            String variance,
            String executionRate
    ) {
    }

    public record UpdateSettlementRequest(
            String reportTitle,
            LocalDate reportDate,
            String preparedBy,
            String approvedBy,
            String summaryNotes,
            BigDecimal totalAllocated,
            BigDecimal totalSpent,
            BigDecimal totalVariance,
            BigDecimal executionRate
    ) {
    }
}
