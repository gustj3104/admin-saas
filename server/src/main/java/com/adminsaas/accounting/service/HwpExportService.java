package com.adminsaas.accounting.service;

import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.List;

@Service
public class HwpExportService {

    public byte[] createEvidenceDocument(HwpEvidencePayload payload) {
        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <hwpml>
                  <document type="evidence">
                    <title>%s</title>
                    <project>%s</project>
                    <paymentDate>%s</paymentDate>
                    <vendor>%s</vendor>
                    <itemName>%s</itemName>
                    <category>%s</category>
                    <subcategory>%s</subcategory>
                    <paymentMethod>%s</paymentMethod>
                    <amount>%s</amount>
                    <notes>%s</notes>
                  </document>
                </hwpml>
                """.formatted(
                escape("지출 증빙 문서"),
                escape(payload.projectName()),
                escape(payload.paymentDate()),
                escape(payload.vendor()),
                escape(payload.itemName()),
                escape(payload.category()),
                escape(payload.subcategory()),
                escape(payload.paymentMethod()),
                escape(payload.amount()),
                escape(payload.notes())
        );
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    public byte[] createSettlementDocument(HwpSettlementPayload payload) {
        StringBuilder itemsXml = new StringBuilder();
        for (HwpSettlementItem item : payload.items()) {
            itemsXml.append("""
                    <item>
                      <category>%s</category>
                      <allocated>%s</allocated>
                      <spent>%s</spent>
                      <variance>%s</variance>
                      <executionRate>%s</executionRate>
                    </item>
                    """.formatted(
                    escape(item.category()),
                    escape(item.allocated()),
                    escape(item.spent()),
                    escape(item.variance()),
                    escape(item.executionRate())
            ));
        }

        String xml = """
                <?xml version="1.0" encoding="UTF-8"?>
                <hwpml>
                  <document type="settlement">
                    <title>%s</title>
                    <project>%s</project>
                    <reportDate>%s</reportDate>
                    <projectPeriod>%s</projectPeriod>
                    <preparedBy>%s</preparedBy>
                    <approvedBy>%s</approvedBy>
                    <summaryNotes>%s</summaryNotes>
                    <totals>
                      <allocated>%s</allocated>
                      <spent>%s</spent>
                      <variance>%s</variance>
                      <executionRate>%s</executionRate>
                    </totals>
                    <items>
                      %s
                    </items>
                  </document>
                </hwpml>
                """.formatted(
                escape(payload.reportTitle()),
                escape(payload.projectName()),
                escape(payload.reportDate()),
                escape(payload.projectPeriod()),
                escape(payload.preparedBy()),
                escape(payload.approvedBy()),
                escape(payload.summaryNotes()),
                escape(payload.totalAllocated()),
                escape(payload.totalSpent()),
                escape(payload.totalVariance()),
                escape(payload.executionRate()),
                itemsXml
        );
        return xml.getBytes(StandardCharsets.UTF_8);
    }

    private String escape(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }

    public record HwpEvidencePayload(
            String projectName,
            String paymentDate,
            String vendor,
            String itemName,
            String category,
            String subcategory,
            String paymentMethod,
            String amount,
            String notes
    ) {
    }

    public record HwpSettlementPayload(
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
            List<HwpSettlementItem> items
    ) {
    }

    public record HwpSettlementItem(
            String category,
            String allocated,
            String spent,
            String variance,
            String executionRate
    ) {
    }
}
