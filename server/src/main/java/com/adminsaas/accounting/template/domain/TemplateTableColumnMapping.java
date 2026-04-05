package com.adminsaas.accounting.template.domain;

public record TemplateTableColumnMapping(
        String id,
        String key,
        String displayName,
        String description,
        String sourceName,
        TemplateFieldType fieldType,
        boolean recommended,
        boolean confirmed,
        boolean createdBySystem
) {
}
