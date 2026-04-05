package com.adminsaas.accounting.service;

import com.adminsaas.accounting.domain.ExpenseEntity;
import com.adminsaas.accounting.domain.ExpenseStatus;
import com.adminsaas.accounting.domain.ProjectEntity;
import com.adminsaas.accounting.repository.ExpenseRepository;
import com.adminsaas.accounting.repository.ProjectRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final ProjectRepository projectRepository;

    public ExpenseService(ExpenseRepository expenseRepository, ProjectRepository projectRepository) {
        this.expenseRepository = expenseRepository;
        this.projectRepository = projectRepository;
    }

    public List<ExpenseEntity> findByProjectId(Long projectId) {
        return expenseRepository.findByProjectIdOrderByPaymentDateDesc(projectId);
    }

    public List<ExpenseEntity> findProcessedByProjectId(Long projectId) {
        return expenseRepository.findByProjectIdAndStatusOrderByPaymentDateDesc(projectId, ExpenseStatus.PROCESSED);
    }

    @Transactional
    public ExpenseEntity save(ExpenseEntity expense) {
        ExpenseEntity saved = expenseRepository.save(expense);
        refreshExecutedBudget(saved.getProject());
        return saved;
    }

    public ExpenseEntity findById(Long id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "지출 기록을 찾을 수 없습니다."));
    }

    @Transactional
    public ExpenseEntity update(Long id, ExpenseEntity changes) {
        ExpenseEntity expense = findById(id);
        expense.setExpenseCode(changes.getExpenseCode());
        expense.setPaymentDate(changes.getPaymentDate());
        expense.setVendor(changes.getVendor());
        expense.setItemName(changes.getItemName());
        expense.setQuantity(changes.getQuantity());
        expense.setUnitPrice(changes.getUnitPrice());
        expense.setCategory(changes.getCategory());
        expense.setSubcategory(changes.getSubcategory());
        expense.setAmount(changes.getAmount());
        expense.setPaymentMethod(changes.getPaymentMethod());
        expense.setNotes(changes.getNotes());
        expense.setStatus(changes.getStatus());
        ExpenseEntity saved = expenseRepository.save(expense);
        refreshExecutedBudget(saved.getProject());
        return saved;
    }

    @Transactional
    public ExpenseEntity updateStatus(Long id, ExpenseStatus status) {
        ExpenseEntity expense = findById(id);
        expense.setStatus(status);
        ExpenseEntity saved = expenseRepository.save(expense);
        refreshExecutedBudget(saved.getProject());
        return saved;
    }

    @Transactional
    public void delete(Long id) {
        ExpenseEntity expense = findById(id);
        ProjectEntity project = expense.getProject();
        expenseRepository.delete(expense);
        refreshExecutedBudget(project);
    }

    private void refreshExecutedBudget(ProjectEntity project) {
        if (project == null || project.getId() == null) {
            return;
        }
        BigDecimal executedBudget = expenseRepository.sumAmountByProjectIdAndStatus(project.getId(), ExpenseStatus.PROCESSED);
        project.setExecutedBudget(executedBudget == null ? BigDecimal.ZERO : executedBudget);
        projectRepository.save(project);
    }
}
