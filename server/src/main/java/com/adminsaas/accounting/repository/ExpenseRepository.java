package com.adminsaas.accounting.repository;

import com.adminsaas.accounting.domain.ExpenseEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ExpenseRepository extends JpaRepository<ExpenseEntity, Long> {
    List<ExpenseEntity> findByProjectIdOrderByPaymentDateDesc(Long projectId);
    void deleteByProjectId(Long projectId);
}
