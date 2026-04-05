package com.adminsaas.accounting.service;

import com.adminsaas.accounting.domain.ExpenseEntity;
import com.adminsaas.accounting.domain.ProjectEntity;
import com.adminsaas.accounting.domain.SettlementReportEntity;
import com.adminsaas.accounting.repository.ExpenseRepository;
import com.adminsaas.accounting.repository.SettlementReportRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Service
public class SettlementService {

    private final SettlementReportRepository settlementReportRepository;
    private final ExpenseRepository expenseRepository;

    public SettlementService(SettlementReportRepository settlementReportRepository, ExpenseRepository expenseRepository) {
        this.settlementReportRepository = settlementReportRepository;
        this.expenseRepository = expenseRepository;
    }

    public SettlementReportEntity latest(Long projectId) {
        return settlementReportRepository.findTopByProjectIdOrderByReportDateDescIdDesc(projectId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "정산 보고서를 찾을 수 없습니다."));
    }

    public SettlementReportEntity generate(ProjectEntity project) {
        List<ExpenseEntity> expenses = expenseRepository.findByProjectIdOrderByPaymentDateDesc(project.getId());
        BigDecimal totalSpent = expenses.stream()
                .map(ExpenseEntity::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalAllocated = project.getTotalBudget();
        BigDecimal totalVariance = totalAllocated.subtract(totalSpent);
        BigDecimal executionRate = totalAllocated.signum() == 0
                ? BigDecimal.ZERO
                : totalSpent.multiply(BigDecimal.valueOf(100))
                .divide(totalAllocated, 2, RoundingMode.HALF_UP);

        SettlementReportEntity report = new SettlementReportEntity();
        report.setProject(project);
        report.setReportTitle("최종 정산 보고서 - " + project.getName());
        report.setReportDate(LocalDate.now());
        report.setPreparedBy("관리자");
        report.setApprovedBy("");
        report.setSummaryNotes("자동 생성된 정산 보고서입니다.");
        report.setTotalAllocated(totalAllocated);
        report.setTotalSpent(totalSpent);
        report.setTotalVariance(totalVariance);
        report.setExecutionRate(executionRate);
        return settlementReportRepository.save(report);
    }

    public SettlementReportEntity update(Long id, SettlementReportEntity changes) {
        SettlementReportEntity report = settlementReportRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "정산 보고서를 찾을 수 없습니다."));
        report.setReportTitle(changes.getReportTitle());
        report.setReportDate(changes.getReportDate());
        report.setPreparedBy(changes.getPreparedBy());
        report.setApprovedBy(changes.getApprovedBy());
        report.setSummaryNotes(changes.getSummaryNotes());
        report.setTotalAllocated(changes.getTotalAllocated());
        report.setTotalSpent(changes.getTotalSpent());
        report.setTotalVariance(changes.getTotalVariance());
        report.setExecutionRate(changes.getExecutionRate());
        return settlementReportRepository.save(report);
    }
}
