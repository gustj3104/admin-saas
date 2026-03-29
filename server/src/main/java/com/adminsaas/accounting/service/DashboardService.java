package com.adminsaas.accounting.service;

import com.adminsaas.accounting.domain.BudgetItemStatus;
import com.adminsaas.accounting.domain.BudgetRuleEntity;
import com.adminsaas.accounting.domain.ExpenseEntity;
import com.adminsaas.accounting.domain.ProjectEntity;
import com.adminsaas.accounting.domain.ValidationResultEntity;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;

@Service
public class DashboardService {

    private final ProjectService projectService;
    private final BudgetRuleService budgetRuleService;
    private final ExpenseService expenseService;
    private final ValidationService validationService;

    public DashboardService(ProjectService projectService,
                            BudgetRuleService budgetRuleService,
                            ExpenseService expenseService,
                            ValidationService validationService) {
        this.projectService = projectService;
        this.budgetRuleService = budgetRuleService;
        this.expenseService = expenseService;
        this.validationService = validationService;
    }

    public DashboardOverview getOverview(Long projectId) {
        ProjectEntity project = projectService.findById(projectId);
        List<BudgetRuleEntity> budgetRules = budgetRuleService.findByProjectId(projectId);
        List<ExpenseEntity> expenses = expenseService.findByProjectId(projectId);
        List<ValidationResultEntity> validations = validationService.findByProjectId(projectId);

        BigDecimal used = expenses.stream().map(ExpenseEntity::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalBudget = project.getTotalBudget();
        BigDecimal remaining = totalBudget.subtract(used);
        long warningCount = budgetRules.stream().filter(rule -> rule.getStatus() == BudgetItemStatus.WARNING).count();
        long errorCount = budgetRules.stream().filter(rule -> rule.getStatus() == BudgetItemStatus.ERROR).count();

        return new DashboardOverview(project.getId(), project.getName(), totalBudget, used, remaining,
                budgetRules.size(), expenses.size(), validations.size(), warningCount, errorCount);
    }

    public record DashboardOverview(
            Long projectId,
            String projectName,
            BigDecimal totalBudget,
            BigDecimal usedBudget,
            BigDecimal remainingBudget,
            int budgetRuleCount,
            int expenseCount,
            int validationCount,
            long warningCount,
            long errorCount
    ) {
    }
}
