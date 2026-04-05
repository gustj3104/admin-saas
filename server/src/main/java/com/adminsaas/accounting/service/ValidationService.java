package com.adminsaas.accounting.service;

import com.adminsaas.accounting.domain.BudgetRuleEntity;
import com.adminsaas.accounting.domain.ExpenseEntity;
import com.adminsaas.accounting.domain.ProjectEntity;
import com.adminsaas.accounting.domain.ValidationSeverity;
import com.adminsaas.accounting.domain.ValidationResultEntity;
import com.adminsaas.accounting.repository.ValidationResultRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

@Service
public class ValidationService {

    private final ValidationResultRepository validationResultRepository;
    private final ProjectService projectService;
    private final ExpenseService expenseService;
    private final BudgetRuleService budgetRuleService;

    private static final Pattern RULE_SPLIT_PATTERN = Pattern.compile("[,\\n/|]+");

    public ValidationService(ValidationResultRepository validationResultRepository,
                             ProjectService projectService,
                             ExpenseService expenseService,
                             BudgetRuleService budgetRuleService) {
        this.validationResultRepository = validationResultRepository;
        this.projectService = projectService;
        this.expenseService = expenseService;
        this.budgetRuleService = budgetRuleService;
    }

    public List<ValidationResultEntity> findByProjectId(Long projectId) {
        return validationResultRepository.findByProjectIdOrderByResultDateDescIdAsc(projectId);
    }

    @Transactional
    public List<ValidationResultEntity> run(Long projectId) {
        ProjectEntity project = projectService.findById(projectId);
        List<ExpenseEntity> expenses = expenseService.findByProjectId(projectId);
        List<BudgetRuleEntity> rules = budgetRuleService.findByProjectId(projectId);

        validationResultRepository.deleteByProjectId(projectId);

        List<ValidationResultEntity> generated = new ArrayList<>();
        for (ExpenseEntity expense : expenses) {
            BudgetRuleEntity matchingRule = rules.stream()
                    .filter(rule -> rule.getCategory().equals(expense.getCategory()) && rule.getSubcategory().equals(expense.getSubcategory()))
                    .findFirst()
                    .orElse(null);

            if (matchingRule == null) {
                generated.add(buildResult(
                        project,
                        "예산 규칙 누락",
                        expense.getCategory(),
                        "지출 " + expense.getExpenseCode() + "에 해당하는 예산 규칙을 찾지 못했습니다.",
                        ValidationSeverity.ERROR,
                        "/budget-plan"
                ));
                continue;
            }

            if (expense.getAmount() != null && matchingRule.getAllocated() != null
                    && expense.getAmount().compareTo(matchingRule.getAllocated()) > 0) {
                generated.add(buildResult(
                        project,
                        "배정액 초과",
                        expense.getCategory(),
                        "지출 " + expense.getExpenseCode() + " 금액이 배정액을 초과했습니다. 배정액: ₩" + matchingRule.getAllocated().toPlainString()
                                + " / 지출액: ₩" + expense.getAmount().toPlainString(),
                        ValidationSeverity.ERROR,
                        "/budget-plan"
                ));
                continue;
            }

            String ruleDescription = matchingRule.getRuleDescription();
            if (ruleDescription != null && !ruleDescription.isBlank() && !matchesRuleDescription(expense, ruleDescription)) {
                generated.add(buildResult(
                        project,
                        "허용된 규칙 설명 불일치",
                        expense.getCategory(),
                        "지출 " + expense.getExpenseCode() + "이(가) 허용된 규칙 설명과 일치하지 않습니다. 규칙: " + ruleDescription,
                        ValidationSeverity.WARNING,
                        "/projects/" + projectId
                ));
                continue;
            }

            generated.add(buildResult(
                    project,
                    "예산 규칙 만족",
                    expense.getCategory(),
                    "지출 " + expense.getExpenseCode() + "이(가) 카테고리/세부 항목 및 허용된 규칙 설명을 만족합니다.",
                    ValidationSeverity.VALID,
                    null
            ));
        }

        return validationResultRepository.saveAll(generated).stream()
                .sorted((left, right) -> right.getResultDate().compareTo(left.getResultDate()))
                .toList();
    }

    public ValidationResultEntity resolve(Long id, String resolutionNote) {
        ValidationResultEntity validation = validationResultRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "검증 결과를 찾을 수 없습니다."));
        validation.setResolved(true);
        validation.setResolutionNote(resolutionNote);
        validation.setResolvedDate(LocalDate.now());
        return validationResultRepository.save(validation);
    }

    private boolean matchesRuleDescription(ExpenseEntity expense, String ruleDescription) {
        String searchable = normalize(String.join(" ",
                expense.getItemName(),
                expense.getVendor(),
                expense.getNotes() == null ? "" : expense.getNotes(),
                expense.getCategory(),
                expense.getSubcategory(),
                expense.getPaymentMethod()
        ));

        return RULE_SPLIT_PATTERN.splitAsStream(ruleDescription)
                .map(String::trim)
                .filter(token -> !token.isBlank())
                .map(this::normalize)
                .anyMatch(token -> token.length() >= 2 && searchable.contains(token));
    }

    private String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replaceAll("\\s+", "");
    }

    private ValidationResultEntity buildResult(ProjectEntity project,
                                               String type,
                                               String category,
                                               String description,
                                               ValidationSeverity severity,
                                               String linkTo) {
        ValidationResultEntity entity = new ValidationResultEntity();
        entity.setProject(project);
        entity.setType(type);
        entity.setCategory(category);
        entity.setDescription(description);
        entity.setSeverity(severity);
        entity.setResultDate(LocalDate.now());
        entity.setLinkTo(linkTo);
        entity.setResolved(false);
        entity.setResolutionNote(null);
        entity.setResolvedDate(null);
        return entity;
    }
}
