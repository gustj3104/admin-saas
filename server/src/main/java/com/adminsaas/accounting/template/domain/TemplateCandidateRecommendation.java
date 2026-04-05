package com.adminsaas.accounting.template.domain;

import java.util.List;

public record TemplateCandidateRecommendation(
        TemplateMappingSchema schema,
        List<NormalizedLabel> detectedLabels,
        List<NormalizedTable> detectedTables,
        TemplateExtractionSummary summary
) {
}
