package com.adminsaas.accounting.template.service;

import com.adminsaas.accounting.template.domain.NormalizedTemplateContent;
import com.adminsaas.accounting.template.domain.TemplateDocumentType;

public interface DocumentTemplateDetector {

    TemplateDocumentType detect(NormalizedTemplateContent content);
}
