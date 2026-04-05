package com.adminsaas.accounting.template.repository;

import com.adminsaas.accounting.template.domain.DocumentTemplate;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Repository
public class InMemoryTemplateRepository implements TemplateRepository {

    private final ConcurrentMap<String, DocumentTemplate> storage = new ConcurrentHashMap<>();

    @Override
    public DocumentTemplate save(DocumentTemplate template) {
        storage.put(template.id(), template);
        return template;
    }

    @Override
    public Optional<DocumentTemplate> findById(String templateId) {
        return Optional.ofNullable(storage.get(templateId));
    }
}
