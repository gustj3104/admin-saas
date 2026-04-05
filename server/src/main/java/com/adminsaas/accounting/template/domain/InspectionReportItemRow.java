package com.adminsaas.accounting.template.domain;

public record InspectionReportItemRow(
        String itemName,
        String spec,
        String quantity,
        String unit,
        String unitPrice,
        String amount
) {
}
