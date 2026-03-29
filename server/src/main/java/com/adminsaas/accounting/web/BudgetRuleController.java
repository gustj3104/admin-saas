package com.adminsaas.accounting.web;

import com.adminsaas.accounting.domain.BudgetItemStatus;
import com.adminsaas.accounting.domain.BudgetRuleEntity;
import com.adminsaas.accounting.domain.ProjectEntity;
import com.adminsaas.accounting.service.BudgetRuleService;
import com.adminsaas.accounting.service.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

@RestController
@RequestMapping("/api/projects/{projectId}/budget-rules")
public class BudgetRuleController {

    private final BudgetRuleService budgetRuleService;
    private final ProjectService projectService;

    public BudgetRuleController(BudgetRuleService budgetRuleService, ProjectService projectService) {
        this.budgetRuleService = budgetRuleService;
        this.projectService = projectService;
    }

    @GetMapping
    public List<BudgetRuleResponse> getBudgetRules(@PathVariable Long projectId) {
        return budgetRuleService.findByProjectId(projectId).stream().map(BudgetRuleResponse::from).toList();
    }

    @PutMapping
    public List<BudgetRuleResponse> replaceBudgetRules(@PathVariable Long projectId,
                                                       @Valid @RequestBody List<BudgetRuleRequest> request) {
        ProjectEntity project = projectService.findById(projectId);
        List<BudgetRuleEntity> rules = request.stream().map(item -> {
            BudgetRuleEntity entity = new BudgetRuleEntity();
            entity.setCategory(item.category());
            entity.setSubcategory(item.subcategory());
            entity.setRuleDescription(item.ruleDescription());
            entity.setAllocated(item.allocated());
            entity.setPercentage(item.percentage());
            entity.setStatus(item.status());
            return entity;
        }).toList();
        return budgetRuleService.replaceForProject(project, rules).stream().map(BudgetRuleResponse::from).toList();
    }

    public record BudgetRuleRequest(
            @NotBlank String category,
            @NotBlank String subcategory,
            @NotBlank String ruleDescription,
            @NotNull BigDecimal allocated,
            @NotNull Integer percentage,
            @NotNull BudgetItemStatus status
    ) {}

    public record BudgetRuleResponse(
            Long id,
            String category,
            String subcategory,
            String ruleDescription,
            BigDecimal allocated,
            Integer percentage,
            BudgetItemStatus status
    ) {
        static BudgetRuleResponse from(BudgetRuleEntity entity) {
            return new BudgetRuleResponse(
                    entity.getId(),
                    entity.getCategory(),
                    entity.getSubcategory(),
                    entity.getRuleDescription(),
                    entity.getAllocated(),
                    entity.getPercentage(),
                    entity.getStatus()
            );
        }
    }
}
