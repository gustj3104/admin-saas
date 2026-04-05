package com.adminsaas.accounting.template.service;

import com.adminsaas.accounting.template.domain.NormalizedTemplateContent;
import com.adminsaas.accounting.template.domain.TemplateCandidateRecommendation;
import com.adminsaas.accounting.template.domain.TemplateDocumentType;

public interface FieldCandidateExtractor {

    boolean supports(TemplateDocumentType documentType);

    TemplateCandidateRecommendation extract(String templateId, NormalizedTemplateContent content);
}
