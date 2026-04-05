package com.adminsaas.accounting.template.domain;

public record TemplateFieldMapping(
        String id,
        String key,
        String displayName,
        String description,
        TemplateSourceType sourceType,
        String sourceName,
        TemplateFieldType fieldType,
        boolean recommended,
        boolean confirmed,
        boolean createdBySystem
) {
}
