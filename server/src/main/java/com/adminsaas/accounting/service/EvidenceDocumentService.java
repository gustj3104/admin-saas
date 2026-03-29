package com.adminsaas.accounting.service;

import com.adminsaas.accounting.domain.EvidenceDocumentEntity;
import com.adminsaas.accounting.repository.EvidenceDocumentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class EvidenceDocumentService {

    private final EvidenceDocumentRepository evidenceDocumentRepository;

    public EvidenceDocumentService(EvidenceDocumentRepository evidenceDocumentRepository) {
        this.evidenceDocumentRepository = evidenceDocumentRepository;
    }

    public List<EvidenceDocumentEntity> findByProjectId(Long projectId) {
        return evidenceDocumentRepository.findByProjectIdOrderByCreatedDateDesc(projectId);
    }

    public EvidenceDocumentEntity findById(Long id) {
        return evidenceDocumentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Evidence document not found"));
    }

    public EvidenceDocumentEntity save(EvidenceDocumentEntity document) {
        return evidenceDocumentRepository.save(document);
    }
}
