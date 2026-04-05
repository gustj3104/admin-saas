package com.adminsaas.accounting.repository;

import com.adminsaas.accounting.domain.ExpenseEntity;
import com.adminsaas.accounting.domain.ExpenseStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.List;

public interface ExpenseRepository extends JpaRepository<ExpenseEntity, Long> {
    List<ExpenseEntity> findByProjectIdOrderByPaymentDateDesc(Long projectId);
    List<ExpenseEntity> findByProjectIdAndStatusOrderByPaymentDateDesc(Long projectId, ExpenseStatus status);

    @Query("select coalesce(sum(e.amount), 0) from ExpenseEntity e where e.project.id = :projectId and e.status = :status")
    BigDecimal sumAmountByProjectIdAndStatus(@Param("projectId") Long projectId, @Param("status") ExpenseStatus status);

    void deleteByProjectId(Long projectId);
}
