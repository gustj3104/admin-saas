package com.adminsaas.accounting.template.domain;

import java.util.List;

public record InspectionReportReceiptBlock(
        String receiptId,
        String title,
        String paymentDate,
        String normalizedPaymentDate,
        String vendor,
        String inspector,
        String contractAmount,
        String receiptImagePath,
        int receiptOrder,
        List<InspectionReportItemRow> itemRows
) {
}
