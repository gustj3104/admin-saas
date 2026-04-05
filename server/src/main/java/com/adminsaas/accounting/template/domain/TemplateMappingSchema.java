package com.adminsaas.accounting.template.domain;

import java.util.List;

public record TemplateMappingSchema(
        String templateId,
        TemplateDocumentType documentType,
        List<TemplateFieldMapping> fields,
        List<TemplateTableMapping> tables
) {
}
