package com.adminsaas.accounting.repository;

import com.adminsaas.accounting.domain.BudgetRuleEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BudgetRuleRepository extends JpaRepository<BudgetRuleEntity, Long> {
    List<BudgetRuleEntity> findByProjectIdOrderByIdAsc(Long projectId);
    void deleteByProjectId(Long projectId);
}
