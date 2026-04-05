package com.adminsaas.accounting.template.domain;

import java.util.List;

public record NormalizedBlock(
        String type,
        String text,
        List<List<String>> rows
) {
}
