package com.adminsaas.accounting.template.service;

import com.adminsaas.accounting.template.domain.NormalizedTable;
import com.adminsaas.accounting.template.domain.NormalizedTemplateContent;
import com.adminsaas.accounting.template.domain.TemplateDocumentType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class InspectionReportTemplateDetector implements DocumentTemplateDetector {

    private static final String TABLE_ITEMS = "\uBB3C\uD488 \uAC80\uC218 \uB0B4\uC5ED";
    private static final Set<String> REQUIRED_LABELS = Set.of(
            "\uAC74\uBA85",
            "\uACB0\uC81C\uC77C\uC790",
            "\uB0A9\uD488\uCC98",
            "\uAC80\uC218\uC790",
            "\uACC4\uC57D\uAE08\uC561"
    );
    private static final Set<String> REQUIRED_HEADERS = Set.of(
            "\uD488\uBA85",
            "\uADDC\uACA9",
            "\uC218\uB7C9",
            "\uB2E8\uC704",
            "\uB2E8\uAC00",
            "\uAE08\uC561"
    );

    @Override
    public TemplateDocumentType detect(NormalizedTemplateContent content) {
        List<String> labelSources = new ArrayList<>();
        content.labels().forEach(label -> {
            labelSources.add(label.label());
            labelSources.add(label.value());
        });
        content.blocks().forEach(block -> {
            if (block.text() != null) {
                labelSources.add(block.text());
            }
            labelSources.addAll(block.rows().stream().flatMap(List::stream).toList());
        });
        content.tables().forEach(table -> {
            labelSources.add(table.title());
            labelSources.addAll(table.headers());
            labelSources.addAll(table.rows().stream().flatMap(List::stream).toList());
        });

        long labelMatches = labelSources.stream()
                .map(this::normalize)
                .filter(value -> REQUIRED_LABELS.stream().map(this::normalize).anyMatch(value::contains))
                .distinct()
                .count();

        boolean tableMatch = content.tables().stream().anyMatch(this::matchesInspectionTable);
        boolean titleMatch = labelSources.stream()
                .map(this::normalize)
                .anyMatch(value -> value.contains(normalize(TABLE_ITEMS)) || value.contains(normalize("물품검수조서")));

        if ((labelMatches >= 3 && tableMatch) || (labelMatches >= 4) || (titleMatch && labelMatches >= 2 && tableMatch)) {
            return TemplateDocumentType.INSPECTION_REPORT;
        }
        return TemplateDocumentType.UNKNOWN;
    }

    private boolean matchesInspectionTable(NormalizedTable table) {
        List<String> normalizedHeaders = table.headers().stream().map(this::normalize).toList();
        long headerMatches = REQUIRED_HEADERS.stream()
                .map(this::normalize)
                .filter(normalizedHeaders::contains)
                .count();

        if (headerMatches >= 4) {
            return true;
        }

        String normalizedTitle = normalize(table.title());
        return normalizedTitle.contains(normalize(TABLE_ITEMS)) && headerMatches >= 3;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(" ", "");
    }
}
