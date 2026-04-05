package com.adminsaas.accounting.template.domain;

import java.util.List;

public record InspectionReportExportGroup(
        String groupKey,
        String normalizedDate,
        List<InspectionReportReceiptEntry> entries
) {
}
