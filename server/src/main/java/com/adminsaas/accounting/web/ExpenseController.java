package com.adminsaas.accounting.web;

import com.adminsaas.accounting.domain.ExpenseEntity;
import com.adminsaas.accounting.domain.ExpenseStatus;
import com.adminsaas.accounting.domain.ProjectEntity;
import com.adminsaas.accounting.service.ExpenseService;
import com.adminsaas.accounting.service.ProjectService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/expenses")
public class ExpenseController {

    private final ExpenseService expenseService;
    private final ProjectService projectService;

    public ExpenseController(ExpenseService expenseService, ProjectService projectService) {
        this.expenseService = expenseService;
        this.projectService = projectService;
    }

    @GetMapping
    public List<ExpenseResponse> getExpenses(@RequestParam Long projectId) {
        return expenseService.findByProjectId(projectId).stream().map(ExpenseResponse::from).toList();
    }

    @PostMapping
    public ExpenseResponse createExpense(@Valid @RequestBody CreateExpenseRequest request) {
        ProjectEntity project = projectService.findById(request.projectId());
        ExpenseEntity expense = new ExpenseEntity();
        applyRequest(expense, request, project);
        return ExpenseResponse.from(expenseService.save(expense));
    }

    @PutMapping("/{id}")
    public ExpenseResponse updateExpense(@PathVariable Long id, @Valid @RequestBody CreateExpenseRequest request) {
        ProjectEntity project = projectService.findById(request.projectId());
        ExpenseEntity expense = new ExpenseEntity();
        applyRequest(expense, request, project);
        return ExpenseResponse.from(expenseService.update(id, expense));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteExpense(@PathVariable Long id) {
        expenseService.delete(id);
    }

    private void applyRequest(ExpenseEntity expense, CreateExpenseRequest request, ProjectEntity project) {
        expense.setExpenseCode(request.expenseCode());
        expense.setProject(project);
        expense.setPaymentDate(request.paymentDate());
        expense.setVendor(request.vendor());
        expense.setItemName(request.itemName());
        expense.setQuantity(request.quantity());
        expense.setUnitPrice(request.unitPrice());
        expense.setCategory(request.category());
        expense.setSubcategory(request.subcategory());
        expense.setAmount(request.amount());
        expense.setPaymentMethod(request.paymentMethod());
        expense.setNotes(request.notes());
        expense.setStatus(request.status());
    }

    public record CreateExpenseRequest(
            @NotNull Long projectId,
            @NotBlank String expenseCode,
            @NotNull LocalDate paymentDate,
            @NotBlank String vendor,
            @NotBlank String itemName,
            BigDecimal quantity,
            BigDecimal unitPrice,
            @NotBlank String category,
            @NotBlank String subcategory,
            @NotNull BigDecimal amount,
            @NotBlank String paymentMethod,
            String notes,
            @NotNull ExpenseStatus status
    ) {}

    public record ExpenseResponse(
            Long id,
            String expenseCode,
            Long projectId,
            LocalDate paymentDate,
            String vendor,
            String itemName,
            BigDecimal quantity,
            BigDecimal unitPrice,
            String category,
            String subcategory,
            BigDecimal amount,
            String paymentMethod,
            String notes,
            ExpenseStatus status
    ) {
        static ExpenseResponse from(ExpenseEntity entity) {
            return new ExpenseResponse(
                    entity.getId(),
                    entity.getExpenseCode(),
                    entity.getProject().getId(),
                    entity.getPaymentDate(),
                    entity.getVendor(),
                    entity.getItemName(),
                    entity.getQuantity(),
                    entity.getUnitPrice(),
                    entity.getCategory(),
                    entity.getSubcategory(),
                    entity.getAmount(),
                    entity.getPaymentMethod(),
                    entity.getNotes(),
                    entity.getStatus()
            );
        }
    }
}
