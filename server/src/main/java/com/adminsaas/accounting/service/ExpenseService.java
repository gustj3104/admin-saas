package com.adminsaas.accounting.service;

import com.adminsaas.accounting.domain.ExpenseEntity;
import com.adminsaas.accounting.repository.ExpenseRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class ExpenseService {

    private final ExpenseRepository expenseRepository;

    public ExpenseService(ExpenseRepository expenseRepository) {
        this.expenseRepository = expenseRepository;
    }

    public List<ExpenseEntity> findByProjectId(Long projectId) {
        return expenseRepository.findByProjectIdOrderByPaymentDateDesc(projectId);
    }

    public ExpenseEntity save(ExpenseEntity expense) {
        return expenseRepository.save(expense);
    }

    public ExpenseEntity findById(Long id) {
        return expenseRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Expense not found"));
    }

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
        return expenseRepository.save(expense);
    }

    public void delete(Long id) {
        expenseRepository.delete(findById(id));
    }
}
