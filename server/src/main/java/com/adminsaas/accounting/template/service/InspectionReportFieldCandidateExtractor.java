package com.adminsaas.accounting.template.service;

import com.adminsaas.accounting.template.domain.NormalizedLabel;
import com.adminsaas.accounting.template.domain.NormalizedTable;
import com.adminsaas.accounting.template.domain.NormalizedTemplateContent;
import com.adminsaas.accounting.template.domain.TemplateCandidateRecommendation;
import com.adminsaas.accounting.template.domain.TemplateDocumentType;
import com.adminsaas.accounting.template.domain.TemplateExtractionSummary;
import com.adminsaas.accounting.template.domain.TemplateFieldMapping;
import com.adminsaas.accounting.template.domain.TemplateFieldType;
import com.adminsaas.accounting.template.domain.TemplateMappingSchema;
import com.adminsaas.accounting.template.domain.TemplateSourceType;
import com.adminsaas.accounting.template.domain.TemplateTableColumnMapping;
import com.adminsaas.accounting.template.domain.TemplateTableMapping;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class InspectionReportFieldCandidateExtractor implements FieldCandidateExtractor {

    private static final String LABEL_TITLE = "\uAC74\uBA85";
    private static final String LABEL_DATE = "\uACB0\uC81C\uC77C\uC790";
    private static final String LABEL_VENDOR = "\uB0A9\uD488\uCC98";
    private static final String LABEL_INSPECTOR = "\uAC80\uC218\uC790";
    private static final String LABEL_CONTRACT_AMOUNT = "\uACC4\uC57D\uAE08\uC561";
    private static final String DISPLAY_INSPECTION_TITLE = "\uAC80\uC218 \uAC74\uBA85";
    private static final String DISPLAY_DATE = "\uACB0\uC81C \uB610\uB294 \uC791\uC131\uC77C";
    private static final String TABLE_ITEMS = "\uBB3C\uD488 \uAC80\uC218 \uB0B4\uC5ED";
    private static final String COLUMN_NAME = "\uD488\uBA85";
    private static final String COLUMN_SPEC = "\uADDC\uACA9";
    private static final String COLUMN_QUANTITY = "\uC218\uB7C9";
    private static final String COLUMN_UNIT = "\uB2E8\uC704";
    private static final String COLUMN_UNIT_PRICE = "\uB2E8\uAC00";
    private static final String COLUMN_AMOUNT = "\uAE08\uC561";

    private final AliasDictionary aliasDictionary;

    public InspectionReportFieldCandidateExtractor(AliasDictionary aliasDictionary) {
        this.aliasDictionary = aliasDictionary;
    }

    @Override
    public boolean supports(TemplateDocumentType documentType) {
        return documentType == TemplateDocumentType.INSPECTION_REPORT;
    }

    @Override
    public TemplateCandidateRecommendation extract(String templateId, NormalizedTemplateContent content) {
        List<TemplateFieldMapping> fields = new ArrayList<>();
        List<String> labelCandidates = collectLabelCandidates(content);

        addFieldIfMatched(fields, labelCandidates, "title", LABEL_TITLE, DISPLAY_INSPECTION_TITLE, TemplateFieldType.STRING);
        addFieldIfMatched(fields, labelCandidates, "date", LABEL_DATE, DISPLAY_DATE, TemplateFieldType.DATE);
        addFieldIfMatched(fields, labelCandidates, "vendor", LABEL_VENDOR, LABEL_VENDOR, TemplateFieldType.STRING);
        addFieldIfMatched(fields, labelCandidates, "inspector", LABEL_INSPECTOR, LABEL_INSPECTOR, TemplateFieldType.STRING);
        addFieldIfMatched(fields, labelCandidates, "contractAmount", LABEL_CONTRACT_AMOUNT, LABEL_CONTRACT_AMOUNT, TemplateFieldType.NUMBER);

        List<TemplateTableMapping> tables = new ArrayList<>();
        findInspectionTable(content.tables()).ifPresent(table -> tables.add(
                new TemplateTableMapping(
                        UUID.randomUUID().toString(),
                        "items",
                        TABLE_ITEMS,
                        table.title(),
                        true,
                        false,
                        true,
                        recommendColumns(table)
                )
        ));

        TemplateMappingSchema schema = new TemplateMappingSchema(templateId, TemplateDocumentType.INSPECTION_REPORT, fields, tables);
        TemplateExtractionSummary summary = new TemplateExtractionSummary(
                content.labels().size(),
                content.tables().size(),
                fields.size(),
                tables.size()
        );
        return new TemplateCandidateRecommendation(schema, content.labels(), content.tables(), summary);
    }

    private List<String> collectLabelCandidates(NormalizedTemplateContent content) {
        LinkedHashSet<String> candidates = new LinkedHashSet<>();
        content.labels().stream()
                .map(NormalizedLabel::label)
                .forEach(candidates::add);

        for (NormalizedTable table : content.tables()) {
            for (List<String> row : table.rows()) {
                candidates.addAll(row);
            }
        }

        return candidates.stream()
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }

    private void addFieldIfMatched(
            List<TemplateFieldMapping> fields,
            List<String> labelCandidates,
            String key,
            String fallbackAlias,
            String displayName,
            TemplateFieldType fieldType
    ) {
        String matchedLabel = findExactAlias(labelCandidates, aliasDictionary.labelAliases().getOrDefault(key, List.of(fallbackAlias)));
        if (matchedLabel == null) {
            return;
        }
        fields.add(new TemplateFieldMapping(
                UUID.randomUUID().toString(),
                key,
                displayName,
                displayName,
                TemplateSourceType.LABEL,
                matchedLabel,
                fieldType,
                true,
                false,
                true
        ));
    }

    private List<TemplateTableColumnMapping> recommendColumns(NormalizedTable table) {
        List<TemplateTableColumnMapping> columns = new ArrayList<>();
        addColumnIfMatched(columns, table.headers(), "name", COLUMN_NAME, COLUMN_NAME, TemplateFieldType.STRING);
        addColumnIfMatched(columns, table.headers(), "spec", COLUMN_SPEC, COLUMN_SPEC, TemplateFieldType.STRING);
        addColumnIfMatched(columns, table.headers(), "quantity", COLUMN_QUANTITY, COLUMN_QUANTITY, TemplateFieldType.NUMBER);
        addColumnIfMatched(columns, table.headers(), "unit", COLUMN_UNIT, COLUMN_UNIT, TemplateFieldType.STRING);
        addColumnIfMatched(columns, table.headers(), "unitPrice", COLUMN_UNIT_PRICE, COLUMN_UNIT_PRICE, TemplateFieldType.NUMBER);
        addColumnIfMatched(columns, table.headers(), "amount", COLUMN_AMOUNT, COLUMN_AMOUNT, TemplateFieldType.NUMBER);
        return columns;
    }

    private void addColumnIfMatched(
            List<TemplateTableColumnMapping> columns,
            List<String> headers,
            String key,
            String fallbackAlias,
            String displayName,
            TemplateFieldType fieldType
    ) {
        String matchedHeader = findExactAlias(headers, aliasDictionary.tableColumnAliases().getOrDefault("items." + key, List.of(fallbackAlias)));
        if (matchedHeader == null) {
            return;
        }
        columns.add(new TemplateTableColumnMapping(
                UUID.randomUUID().toString(),
                key,
                displayName,
                displayName,
                matchedHeader,
                fieldType,
                true,
                false,
                true
        ));
    }

    private java.util.Optional<NormalizedTable> findInspectionTable(List<NormalizedTable> tables) {
        List<String> tableAliases = aliasDictionary.tableAliases().getOrDefault("items", List.of(TABLE_ITEMS));
        return tables.stream()
                .filter(table -> findExactAlias(List.of(table.title()), tableAliases) != null)
                .findFirst();
    }

    private String findExactAlias(List<String> candidates, List<String> aliases) {
        var normalizedByOriginal = candidates.stream()
                .collect(Collectors.toMap(
                        this::normalize,
                        Function.identity(),
                        (left, right) -> left
                ));
        for (String alias : aliases) {
            String matched = normalizedByOriginal.get(normalize(alias));
            if (matched != null) {
                return matched;
            }
        }
        return null;
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replace(" ", "");
    }
}
