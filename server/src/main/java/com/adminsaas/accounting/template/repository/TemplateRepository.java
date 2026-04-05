package com.adminsaas.accounting.template.repository;

import com.adminsaas.accounting.template.domain.DocumentTemplate;

import java.util.Optional;

public interface TemplateRepository {

    DocumentTemplate save(DocumentTemplate template);

    Optional<DocumentTemplate> findById(String templateId);
}
