package com.adminsaas.accounting.service;

import com.adminsaas.accounting.domain.ProjectEntity;
import com.adminsaas.accounting.repository.BudgetRuleRepository;
import com.adminsaas.accounting.repository.EvidenceDocumentRepository;
import com.adminsaas.accounting.repository.ExpenseRepository;
import com.adminsaas.accounting.repository.ProjectRepository;
import com.adminsaas.accounting.repository.SettlementReportRepository;
import com.adminsaas.accounting.repository.ValidationResultRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

@Service
public class ProjectService {

    private final ProjectRepository projectRepository;
    private final BudgetRuleRepository budgetRuleRepository;
    private final ExpenseRepository expenseRepository;
    private final EvidenceDocumentRepository evidenceDocumentRepository;
    private final ValidationResultRepository validationResultRepository;
    private final SettlementReportRepository settlementReportRepository;
    private final FileStorageService fileStorageService;

    public ProjectService(ProjectRepository projectRepository,
                          BudgetRuleRepository budgetRuleRepository,
                          ExpenseRepository expenseRepository,
                          EvidenceDocumentRepository evidenceDocumentRepository,
                          ValidationResultRepository validationResultRepository,
                          SettlementReportRepository settlementReportRepository,
                          FileStorageService fileStorageService) {
        this.projectRepository = projectRepository;
        this.budgetRuleRepository = budgetRuleRepository;
        this.expenseRepository = expenseRepository;
        this.evidenceDocumentRepository = evidenceDocumentRepository;
        this.validationResultRepository = validationResultRepository;
        this.settlementReportRepository = settlementReportRepository;
        this.fileStorageService = fileStorageService;
    }

    public List<ProjectEntity> findAll() {
        return projectRepository.findAll();
    }

    public ProjectEntity findById(Long id) {
        return projectRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "프로젝트를 찾을 수 없습니다."));
    }

    public ProjectEntity save(ProjectEntity project) {
        return projectRepository.save(project);
    }

    public ProjectEntity update(Long id, ProjectEntity projectChanges) {
        ProjectEntity project = findById(id);
        project.setName(projectChanges.getName());
        project.setDescription(projectChanges.getDescription());
        project.setTotalBudget(projectChanges.getTotalBudget());
        project.setExecutedBudget(projectChanges.getExecutedBudget());
        project.setStatus(projectChanges.getStatus());
        project.setStartDate(projectChanges.getStartDate());
        project.setEndDate(projectChanges.getEndDate());
        return projectRepository.save(project);
    }

    @Transactional
    public void delete(Long id) throws IOException {
        ProjectEntity project = findById(id);
        evidenceDocumentRepository.deleteByProjectId(id);
        expenseRepository.deleteByProjectId(id);
        budgetRuleRepository.deleteByProjectId(id);
        validationResultRepository.deleteByProjectId(id);
        settlementReportRepository.deleteByProjectId(id);
        projectRepository.delete(project);
        fileStorageService.deleteProjectDirectory(id);
    }
}
