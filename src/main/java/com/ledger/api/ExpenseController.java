package com.ledger.api;

import com.ledger.api.dto.ExpenseCategoryRequest;
import com.ledger.api.dto.ExpenseCategoryResponse;
import com.ledger.api.dto.ExpenseRequest;
import com.ledger.api.dto.ExpenseResponse;
import com.ledger.api.dto.ExpenseSummaryResponse;
import com.ledger.api.dto.PageResponse;
import com.ledger.application.ExpenseService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Expenses")
public class ExpenseController {

    private final ExpenseService expenses;

    public ExpenseController(ExpenseService expenses) {
        this.expenses = expenses;
    }

    /* ── categories ── */

    @GetMapping("/expense-categories")
    @Operation(summary = "Категории трат текущего пользователя")
    public List<ExpenseCategoryResponse> listCategories() {
        return expenses.listCategories();
    }

    @PostMapping("/expense-categories")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Создать категорию трат")
    public ExpenseCategoryResponse createCategory(@Valid @RequestBody ExpenseCategoryRequest request) {
        return expenses.createCategory(request);
    }

    @PutMapping("/expense-categories/{id}")
    @Operation(summary = "Переименовать категорию трат")
    public ExpenseCategoryResponse updateCategory(@PathVariable UUID id, @Valid @RequestBody ExpenseCategoryRequest request) {
        return expenses.updateCategory(id, request);
    }

    @DeleteMapping("/expense-categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить категорию трат")
    public void deleteCategory(@PathVariable UUID id) {
        expenses.deleteCategory(id);
    }

    /* ── expenses ── */

    @GetMapping(value = "/expenses", params = "!page")
    @Operation(summary = "Траты за диапазон месяцев (yyyy-MM)")
    public List<ExpenseResponse> list(@RequestParam String from, @RequestParam String to) {
        return expenses.list(from, to);
    }

    @GetMapping(value = "/expenses", params = "page")
    @Operation(summary = "Траты за диапазон месяцев с пагинацией")
    public PageResponse<ExpenseResponse> listPaged(
            @RequestParam String from,
            @RequestParam String to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return expenses.listPaged(from, to, page, size);
    }

    @GetMapping("/expenses/summary")
    @Operation(summary = "Сводка по тратам: суммы по категориям по месяцам")
    public ExpenseSummaryResponse summary(@RequestParam String from, @RequestParam String to) {
        return expenses.summary(from, to);
    }

    @GetMapping("/expenses/{id}")
    public ExpenseResponse get(@PathVariable UUID id) {
        return expenses.get(id);
    }

    @PostMapping("/expenses")
    @ResponseStatus(HttpStatus.CREATED)
    public ExpenseResponse create(@Valid @RequestBody ExpenseRequest request) {
        return expenses.create(request);
    }

    @PutMapping("/expenses/{id}")
    public ExpenseResponse update(@PathVariable UUID id, @Valid @RequestBody ExpenseRequest request) {
        return expenses.update(id, request);
    }

    @DeleteMapping("/expenses/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable UUID id) {
        expenses.delete(id);
    }

    @DeleteMapping("/expenses")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Удалить все траты категории за год")
    public int deleteByCategory(@RequestParam UUID categoryId, @RequestParam int year) {
        return expenses.deleteByCategory(categoryId, year);
    }
}
