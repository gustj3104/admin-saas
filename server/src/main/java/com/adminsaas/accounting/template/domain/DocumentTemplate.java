package com.adminsaas.accounting.template.domain;

import java.time.OffsetDateTime;

public record DocumentTemplate(
        String id,
        String filename,
        String contentType,
        long size,
        TemplateDocumentType documentType,
        TemplateStatus status,
        OffsetDateTime uploadedAt,
        NormalizedTemplateContent structuredContent,
        TemplateExtractionSummary extractionSummary,
        TemplateMappingSchema mappingSchema
) {
}
