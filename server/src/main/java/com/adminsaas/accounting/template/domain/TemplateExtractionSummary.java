package com.adminsaas.accounting.template.domain;

public record TemplateExtractionSummary(
        int labelCount,
        int tableCount,
        int recommendedFieldCount,
        int recommendedTableCount
) {
}
