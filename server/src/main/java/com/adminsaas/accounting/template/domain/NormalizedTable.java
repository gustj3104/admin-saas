package com.adminsaas.accounting.template.domain;

import java.util.List;

public record NormalizedTable(
        String title,
        List<String> headers,
        List<List<String>> rows
) {
}
