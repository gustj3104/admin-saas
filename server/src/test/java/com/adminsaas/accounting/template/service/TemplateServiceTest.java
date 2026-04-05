package com.adminsaas.accounting.template.service;

import com.adminsaas.accounting.template.domain.NormalizedBlock;
import com.adminsaas.accounting.template.domain.NormalizedLabel;
import com.adminsaas.accounting.template.domain.NormalizedTable;
import com.adminsaas.accounting.template.domain.NormalizedTemplateContent;
import com.adminsaas.accounting.template.domain.TemplateCandidateRecommendation;
import com.adminsaas.accounting.template.domain.TemplateMappingSchema;
import com.adminsaas.accounting.template.domain.TemplateStatus;
import com.adminsaas.accounting.template.repository.InMemoryTemplateRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TemplateServiceTest {

    private final TemplateService templateService = new TemplateService(
            new InMemoryTemplateRepository(),
            List.of(),
            List.of(new InspectionReportFieldCandidateExtractor(new AliasDictionary())),
            new InspectionReportTemplateDetector(),
            new ObjectMapper()
    );

    @Test
    void savesAndLoadsMappings() {
        NormalizedTemplateContent content = new NormalizedTemplateContent(
                "inspection_report",
                List.of(new NormalizedBlock("text", "\uBB3C\uD488\uAC80\uC218\uC870\uC11C", List.of())),
                List.of(
                        new NormalizedLabel("\uAC74\uBA85", ""),
                        new NormalizedLabel("\uACB0\uC81C\uC77C\uC790", ""),
                        new NormalizedLabel("\uB0A9\uD488\uCC98", ""),
                        new NormalizedLabel("\uAC80\uC218\uC790", ""),
                        new NormalizedLabel("\uACC4\uC57D\uAE08\uC561", "")
                ),
                List.of(new NormalizedTable(
                        "\uBB3C\uD488 \uAC80\uC218 \uB0B4\uC5ED",
                        List.of("\uD488\uBA85", "\uADDC\uACA9", "\uC218\uB7C9", "\uB2E8\uC704", "\uB2E8\uAC00", "\uAE08\uC561"),
                        List.of()
                ))
        );

        String templateId = templateService.uploadJson("inspection.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document", content).id();
        TemplateCandidateRecommendation extracted = templateService.extract(templateId);
        TemplateMappingSchema saved = templateService.updateMappings(templateId, extracted.schema());

        assertThat(templateService.getMappings(templateId).fields()).hasSize(5);
        assertThat(templateService.getTemplate(templateId).status()).isEqualTo(TemplateStatus.MAPPED);
        assertThat(saved.templateId()).isEqualTo(templateId);
    }
}
