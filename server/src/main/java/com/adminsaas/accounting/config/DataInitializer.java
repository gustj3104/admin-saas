package com.adminsaas.accounting.config;

import com.adminsaas.accounting.domain.BudgetItemStatus;
import com.adminsaas.accounting.domain.BudgetRuleEntity;
import com.adminsaas.accounting.domain.DocumentStatus;
import com.adminsaas.accounting.domain.EvidenceDocumentEntity;
import com.adminsaas.accounting.domain.ExpenseEntity;
import com.adminsaas.accounting.domain.ExpenseStatus;
import com.adminsaas.accounting.domain.ProjectEntity;
import com.adminsaas.accounting.domain.ProjectStatus;
import com.adminsaas.accounting.domain.SettlementReportEntity;
import com.adminsaas.accounting.domain.ValidationResultEntity;
import com.adminsaas.accounting.domain.ValidationSeverity;
import com.adminsaas.accounting.repository.BudgetRuleRepository;
import com.adminsaas.accounting.repository.EvidenceDocumentRepository;
import com.adminsaas.accounting.repository.ExpenseRepository;
import com.adminsaas.accounting.repository.ProjectRepository;
import com.adminsaas.accounting.repository.SettlementReportRepository;
import com.adminsaas.accounting.repository.ValidationResultRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    private final ProjectRepository projectRepository;
    private final BudgetRuleRepository budgetRuleRepository;
    private final ExpenseRepository expenseRepository;
    private final EvidenceDocumentRepository evidenceDocumentRepository;
    private final ValidationResultRepository validationResultRepository;
    private final SettlementReportRepository settlementReportRepository;

    public DataInitializer(ProjectRepository projectRepository,
                           BudgetRuleRepository budgetRuleRepository,
                           ExpenseRepository expenseRepository,
                           EvidenceDocumentRepository evidenceDocumentRepository,
                           ValidationResultRepository validationResultRepository,
                           SettlementReportRepository settlementReportRepository) {
        this.projectRepository = projectRepository;
        this.budgetRuleRepository = budgetRuleRepository;
        this.expenseRepository = expenseRepository;
        this.evidenceDocumentRepository = evidenceDocumentRepository;
        this.validationResultRepository = validationResultRepository;
        this.settlementReportRepository = settlementReportRepository;
    }

    @Override
    public void run(String... args) {
        if (projectRepository.count() > 0) {
            return;
        }

        ProjectEntity project = new ProjectEntity();
        project.setName("2024 Grant Project A");
        project.setDescription("회계 및 증빙 자동화를 위한 관리자 프로젝트");
        project.setTotalBudget(BigDecimal.valueOf(50_000_000L));
        project.setExecutedBudget(BigDecimal.valueOf(32_500_000L));
        project.setStatus(ProjectStatus.ACTIVE);
        project.setStartDate(LocalDate.of(2024, 1, 1));
        project.setEndDate(LocalDate.of(2024, 12, 31));
        project = projectRepository.save(project);

        budgetRuleRepository.saveAll(List.of(
                budgetRule(project, "인건비", "급여", "전체의 최대 50%", 20_000_000L, 40, BudgetItemStatus.VALID),
                budgetRule(project, "인건비", "자문 수수료", "전체의 최대 50%", 5_000_000L, 10, BudgetItemStatus.VALID),
                budgetRule(project, "장비 구매", "컴퓨터", "전체의 최대 20%", 8_000_000L, 16, BudgetItemStatus.VALID),
                budgetRule(project, "장비 구매", "소프트웨어", "전체의 최대 20%", 2_000_000L, 4, BudgetItemStatus.VALID),
                budgetRule(project, "여행비", "국내", "전체의 최대 10%", 3_000_000L, 6, BudgetItemStatus.VALID),
                budgetRule(project, "여행비", "해외", "전체의 최대 10%", 2_000_000L, 4, BudgetItemStatus.VALID),
                budgetRule(project, "자재비", "실험 용품", "전체의 최대 15%", 7_000_000L, 14, BudgetItemStatus.VALID),
                budgetRule(project, "간접비", "행정비", "전체의 최대 10%", 3_000_000L, 6, BudgetItemStatus.WARNING)
        ));

        ExpenseEntity exp1 = expense(project, "EXP-001", "Office Depot", "프린터 용지 A4", "자재비", "실험 용품", 45_000L, "CARD", LocalDate.of(2026, 3, 27), ExpenseStatus.PROCESSED);
        ExpenseEntity exp2 = expense(project, "EXP-002", "대한항공", "부산행 항공권", "여행비", "국내", 180_000L, "CARD", LocalDate.of(2026, 3, 26), ExpenseStatus.PROCESSED);
        ExpenseEntity exp3 = expense(project, "EXP-003", "Tech Store", "USB 드라이브 128GB", "장비 구매", "컴퓨터", 35_000L, "BANK_TRANSFER", LocalDate.of(2026, 3, 25), ExpenseStatus.PENDING);
        expenseRepository.saveAll(List.of(exp1, exp2, exp3));

        evidenceDocumentRepository.saveAll(List.of(
                document(project, exp1, "구매 영수증", "Office Depot", 45_000L, LocalDate.of(2026, 3, 27), DocumentStatus.CONFIRMED),
                document(project, exp2, "여행 증빙", "대한항공", 180_000L, LocalDate.of(2026, 3, 26), DocumentStatus.CONFIRMED),
                document(project, exp3, "구매 영수증", "Tech Store", 35_000L, LocalDate.of(2026, 3, 25), DocumentStatus.DRAFT)
        ));

        validationResultRepository.saveAll(List.of(
                validation(project, "예산 불일치", "인건비", "배정액: ₩25,000,000 | 실제: ₩25,500,000", ValidationSeverity.ERROR, LocalDate.of(2026, 3, 27), "/budget-plan"),
                validation(project, "한도 초과", "장비 구매", "카테고리가 20% 한도를 초과했습니다 (현재 22%)", ValidationSeverity.WARNING, LocalDate.of(2026, 3, 27), "/budget-plan"),
                validation(project, "문서 누락", "여행비", "지출 EXP-015의 영수증 첨부 누락", ValidationSeverity.ERROR, LocalDate.of(2026, 3, 26), "/evidence-documents"),
                validation(project, "예산 준수", "자재비", "모든 지출이 예산 한도 내에 있음", ValidationSeverity.VALID, LocalDate.of(2026, 3, 27), null)
        ));

        SettlementReportEntity report = new SettlementReportEntity();
        report.setProject(project);
        report.setReportTitle("최종 정산 보고서 - 2024 Grant Project A");
        report.setReportDate(LocalDate.of(2026, 3, 28));
        report.setPreparedBy("관리자");
        report.setApprovedBy("");
        report.setSummaryNotes("초기 시드 데이터 기반 보고서입니다.");
        report.setTotalAllocated(project.getTotalBudget());
        report.setTotalSpent(project.getExecutedBudget());
        report.setTotalVariance(project.getTotalBudget().subtract(project.getExecutedBudget()));
        report.setExecutionRate(project.getExecutedBudget()
                .multiply(BigDecimal.valueOf(100))
                .divide(project.getTotalBudget(), 2, RoundingMode.HALF_UP));
        settlementReportRepository.save(report);
    }

    private BudgetRuleEntity budgetRule(ProjectEntity project, String category, String subcategory, String ruleDescription,
                                        long allocated, int percentage, BudgetItemStatus status) {
        BudgetRuleEntity entity = new BudgetRuleEntity();
        entity.setProject(project);
        entity.setCategory(category);
        entity.setSubcategory(subcategory);
        entity.setRuleDescription(ruleDescription);
        entity.setAllocated(BigDecimal.valueOf(allocated));
        entity.setPercentage(percentage);
        entity.setStatus(status);
        return entity;
    }

    private ExpenseEntity expense(ProjectEntity project, String code, String vendor, String itemName, String category,
                                  String subcategory, long amount, String paymentMethod, LocalDate paymentDate, ExpenseStatus status) {
        ExpenseEntity entity = new ExpenseEntity();
        entity.setProject(project);
        entity.setExpenseCode(code);
        entity.setVendor(vendor);
        entity.setItemName(itemName);
        entity.setCategory(category);
        entity.setSubcategory(subcategory);
        entity.setAmount(BigDecimal.valueOf(amount));
        entity.setPaymentMethod(paymentMethod);
        entity.setPaymentDate(paymentDate);
        entity.setNotes("");
        entity.setStatus(status);
        return entity;
    }

    private EvidenceDocumentEntity document(ProjectEntity project, ExpenseEntity expense, String type, String vendor,
                                            long amount, LocalDate createdDate, DocumentStatus status) {
        EvidenceDocumentEntity entity = new EvidenceDocumentEntity();
        entity.setProject(project);
        entity.setExpense(expense);
        entity.setDocumentType(type);
        entity.setVendor(vendor);
        entity.setAmount(BigDecimal.valueOf(amount));
        entity.setCreatedDate(createdDate);
        entity.setStatus(status);
        return entity;
    }

    private ValidationResultEntity validation(ProjectEntity project, String type, String category, String description,
                                              ValidationSeverity severity, LocalDate date, String linkTo) {
        ValidationResultEntity entity = new ValidationResultEntity();
        entity.setProject(project);
        entity.setType(type);
        entity.setCategory(category);
        entity.setDescription(description);
        entity.setSeverity(severity);
        entity.setResultDate(date);
        entity.setLinkTo(linkTo);
        return entity;
    }
}
