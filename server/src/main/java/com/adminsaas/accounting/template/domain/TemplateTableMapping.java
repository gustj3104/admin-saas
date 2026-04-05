package com.adminsaas.accounting.template.domain;

import java.util.List;

public record TemplateTableMapping(
        String id,
        String key,
        String displayName,
        String sourceName,
        boolean recommended,
        boolean confirmed,
        boolean createdBySystem,
        List<TemplateTableColumnMapping> columns
) {
}
