package com.adminsaas.accounting.repository;

import com.adminsaas.accounting.domain.EvidenceDocumentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface EvidenceDocumentRepository extends JpaRepository<EvidenceDocumentEntity, Long> {
    List<EvidenceDocumentEntity> findByProjectIdOrderByCreatedDateDesc(Long projectId);
    void deleteByProjectId(Long projectId);
}
