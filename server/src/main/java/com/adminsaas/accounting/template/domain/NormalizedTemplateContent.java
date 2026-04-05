package com.adminsaas.accounting.template.domain;

import java.util.List;

public record NormalizedTemplateContent(
        String documentType,
        List<NormalizedBlock> blocks,
        List<NormalizedLabel> labels,
        List<NormalizedTable> tables
) {
}
