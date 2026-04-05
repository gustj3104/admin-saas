package com.adminsaas.accounting.template.service;

import com.adminsaas.accounting.template.domain.NormalizedBlock;
import com.adminsaas.accounting.template.domain.NormalizedLabel;
import com.adminsaas.accounting.template.domain.NormalizedTable;
import com.adminsaas.accounting.template.domain.NormalizedTemplateContent;
import com.adminsaas.accounting.template.domain.TemplateCandidateRecommendation;
import com.adminsaas.accounting.template.domain.TemplateDocumentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InspectionReportFieldCandidateExtractorTest {

    private static final String LABEL_TITLE = "\uAC74\uBA85";
    private static final String LABEL_DATE = "\uACB0\uC81C\uC77C\uC790";
    private static final String LABEL_VENDOR = "\uB0A9\uD488\uCC98";
    private static final String LABEL_INSPECTOR = "\uAC80\uC218\uC790";
    private static final String LABEL_CONTRACT_AMOUNT = "\uACC4\uC57D\uAE08\uC561";
    private static final String TABLE_ITEMS = "\uBB3C\uD488 \uAC80\uC218 \uB0B4\uC5ED";
    private static final String COLUMN_NAME = "\uD488\uBA85";
    private static final String COLUMN_SPEC = "\uADDC\uACA9";
    private static final String COLUMN_QUANTITY = "\uC218\uB7C9";
    private static final String COLUMN_UNIT = "\uB2E8\uC704";
    private static final String COLUMN_UNIT_PRICE = "\uB2E8\uAC00";
    private static final String COLUMN_AMOUNT = "\uAE08\uC561";

    private final InspectionReportFieldCandidateExtractor extractor = new InspectionReportFieldCandidateExtractor(new AliasDictionary());

    @Test
    void extractsRecommendedFieldsAndTableColumns() {
        NormalizedTemplateContent content = new NormalizedTemplateContent(
                "inspection_report",
                List.of(new NormalizedBlock("text", "\uBB3C\uD488\uAC80\uC218\uC870\uC11C", List.of())),
                List.of(
                        new NormalizedLabel(LABEL_TITLE, ""),
                        new NormalizedLabel(LABEL_DATE, ""),
                        new NormalizedLabel(LABEL_VENDOR, ""),
                        new NormalizedLabel(LABEL_INSPECTOR, ""),
                        new NormalizedLabel(LABEL_CONTRACT_AMOUNT, "")
                ),
                List.of(new NormalizedTable(TABLE_ITEMS, List.of(COLUMN_NAME, COLUMN_SPEC, COLUMN_QUANTITY, COLUMN_UNIT, COLUMN_UNIT_PRICE, COLUMN_AMOUNT), List.of()))
        );

        TemplateCandidateRecommendation recommendation = extractor.extract("tpl_1", content);

        assertThat(extractor.supports(TemplateDocumentType.INSPECTION_REPORT)).isTrue();
        assertThat(recommendation.schema().fields()).hasSize(5);
        assertThat(recommendation.schema().tables()).hasSize(1);
        assertThat(recommendation.schema().tables().get(0).columns()).hasSize(6);
        assertThat(recommendation.schema().fields())
                .extracting(field -> field.key() + ":" + field.sourceName())
                .contains("date:" + LABEL_DATE, "vendor:" + LABEL_VENDOR, "contractAmount:" + LABEL_CONTRACT_AMOUNT);
    }

    @Test
    void extractsFieldCandidatesFromTableRowsWhenLabelListIsEmpty() {
        NormalizedTemplateContent content = new NormalizedTemplateContent(
                "inspection_report",
                List.of(),
                List.of(),
                List.of(new NormalizedTable(
                        TABLE_ITEMS,
                        List.of(COLUMN_NAME, COLUMN_SPEC, COLUMN_QUANTITY, COLUMN_UNIT, COLUMN_UNIT_PRICE, COLUMN_AMOUNT),
                        List.of(
                                List.of(LABEL_TITLE, "-", LABEL_DATE, "2014-11-03"),
                                List.of(LABEL_VENDOR, "\uD14C\uD06C\uC0C1\uC0AC", LABEL_INSPECTOR, "\uD64D\uAE38\uB3D9"),
                                List.of(LABEL_CONTRACT_AMOUNT, "8560")
                        )
                ))
        );

        TemplateCandidateRecommendation recommendation = extractor.extract("tpl_2", content);

        assertThat(recommendation.schema().fields())
                .extracting(field -> field.key() + ":" + field.sourceName())
                .contains(
                        "title:" + LABEL_TITLE,
                        "date:" + LABEL_DATE,
                        "vendor:" + LABEL_VENDOR,
                        "inspector:" + LABEL_INSPECTOR,
                        "contractAmount:" + LABEL_CONTRACT_AMOUNT
                );
    }

    @Test
    void createsMappingsEvenWhenMetadataValuesArePlaceholders() {
        NormalizedTemplateContent content = new NormalizedTemplateContent(
                "inspection_report",
                List.of(),
                List.of(
                        new NormalizedLabel(LABEL_TITLE, ""),
                        new NormalizedLabel(LABEL_INSPECTOR, "")
                ),
                List.of(new NormalizedTable(
                        "\uAE30\uBCF8 \uC815\uBCF4",
                        List.of("label", "value", "label", "value"),
                        List.of(
                                List.of(LABEL_TITLE, "-", LABEL_INSPECTOR, "-")
                        )
                ))
        );

        TemplateCandidateRecommendation recommendation = extractor.extract("tpl_3", content);

        assertThat(recommendation.schema().fields())
                .extracting(field -> field.key() + ":" + field.sourceName())
                .contains("title:" + LABEL_TITLE, "inspector:" + LABEL_INSPECTOR);
    }
}
