package com.adminsaas.accounting.repository;

import com.adminsaas.accounting.domain.SettlementReportEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface SettlementReportRepository extends JpaRepository<SettlementReportEntity, Long> {
    Optional<SettlementReportEntity> findTopByProjectIdOrderByReportDateDescIdDesc(Long projectId);
    void deleteByProjectId(Long projectId);
}
