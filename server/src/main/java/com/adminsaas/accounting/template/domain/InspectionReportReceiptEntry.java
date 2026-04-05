package com.adminsaas.accounting.template.domain;

import java.util.List;

public record InspectionReportReceiptEntry(
        String receiptId,
        String title,
        String paymentDate,
        String vendor,
        String inspector,
        String contractAmount,
        String receiptImagePath,
        int receiptOrder,
        List<InspectionReportItemRow> itemRows
) {
}
