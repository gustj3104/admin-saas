package com.adminsaas.accounting.service;

import com.adminsaas.accounting.domain.BudgetRuleEntity;
import com.adminsaas.accounting.domain.ProjectEntity;
import com.adminsaas.accounting.repository.BudgetRuleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class BudgetRuleService {

    private final BudgetRuleRepository budgetRuleRepository;

    public BudgetRuleService(BudgetRuleRepository budgetRuleRepository) {
        this.budgetRuleRepository = budgetRuleRepository;
    }

    public List<BudgetRuleEntity> findByProjectId(Long projectId) {
        return budgetRuleRepository.findByProjectIdOrderByIdAsc(projectId);
    }

    @Transactional
    public List<BudgetRuleEntity> replaceForProject(ProjectEntity project, List<BudgetRuleEntity> rules) {
        budgetRuleRepository.deleteByProjectId(project.getId());
        rules.forEach(rule -> rule.setProject(project));
        return budgetRuleRepository.saveAll(rules);
    }
}
