package com.adminsaas.accounting.template.domain;

import java.util.List;

public record InspectionReportExportJob(
        String templateId,
        InspectionReportExportMode mode,
        List<InspectionReportExportGroup> groups,
        List<InspectionReportGeneratedDocument> documents
) {
}
