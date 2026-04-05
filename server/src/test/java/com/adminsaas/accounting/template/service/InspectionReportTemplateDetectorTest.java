package com.adminsaas.accounting.template.service;

import com.adminsaas.accounting.template.domain.NormalizedBlock;
import com.adminsaas.accounting.template.domain.NormalizedLabel;
import com.adminsaas.accounting.template.domain.NormalizedTable;
import com.adminsaas.accounting.template.domain.NormalizedTemplateContent;
import com.adminsaas.accounting.template.domain.TemplateDocumentType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InspectionReportTemplateDetectorTest {

    private static final String TABLE_ITEMS = "\uBB3C\uD488 \uAC80\uC218 \uB0B4\uC5ED";
    private static final List<String> REQUIRED_HEADERS = List.of(
            "\uD488\uBA85",
            "\uADDC\uACA9",
            "\uC218\uB7C9",
            "\uB2E8\uC704",
            "\uB2E8\uAC00",
            "\uAE08\uC561"
    );

    private final InspectionReportTemplateDetector detector = new InspectionReportTemplateDetector();

    @Test
    void detectsInspectionReportWhenLabelsAndTableMatch() {
        NormalizedTemplateContent content = new NormalizedTemplateContent(
                "inspection_report",
                List.of(new NormalizedBlock("text", "\uBB3C\uD488\uAC80\uC218\uC870\uC11C", List.of())),
                List.of(
                        new NormalizedLabel("\uAC74\uBA85", ""),
                        new NormalizedLabel("\uACB0\uC81C\uC77C\uC790", ""),
                        new NormalizedLabel("\uB0A9\uD488\uCC98", ""),
                        new NormalizedLabel("\uAC80\uC218\uC790", "")
                ),
                List.of(new NormalizedTable(TABLE_ITEMS, REQUIRED_HEADERS, List.of()))
        );

        assertThat(detector.detect(content)).isEqualTo(TemplateDocumentType.INSPECTION_REPORT);
    }

    @Test
    void returnsUnknownWhenSignalsAreInsufficient() {
        NormalizedTemplateContent content = new NormalizedTemplateContent(
                "unknown",
                List.of(),
                List.of(new NormalizedLabel("\uD504\uB85C\uC81D\uD2B8\uBA85", "")),
                List.of()
        );

        assertThat(detector.detect(content)).isEqualTo(TemplateDocumentType.UNKNOWN);
    }
}
