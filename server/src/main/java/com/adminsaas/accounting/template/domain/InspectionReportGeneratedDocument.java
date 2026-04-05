package com.adminsaas.accounting.template.domain;

import java.util.List;

public record InspectionReportGeneratedDocument(
        String groupKey,
        String title,
        InspectionReportDocumentHead head,
        List<InspectionReportReceiptBlock> receiptBlocks
) {
}
