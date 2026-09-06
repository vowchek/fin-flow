package com.ledger.application;

import com.ledger.api.dto.ExpenseCategoryRequest;
import com.ledger.api.dto.ExpenseCategoryResponse;
import com.ledger.api.dto.ExpenseRequest;
import com.ledger.api.dto.ExpenseResponse;
import com.ledger.api.dto.ExpenseSummaryResponse;
import com.ledger.domain.AppUser;
import com.ledger.domain.ExpenseCategoryEntity;
import com.ledger.domain.ExpenseEntry;
import com.ledger.infrastructure.persistence.ExpenseCategoryRepository;
import com.ledger.infrastructure.persistence.ExpenseEntryRepository;
import com.ledger.infrastructure.persistence.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ExpenseService {

    private final ExpenseEntryRepository expenses;
    private final ExpenseCategoryRepository categoryRepo;
    private final UserRepository users;
    private final CurrentUserService currentUser;
    private final String defaultCurrency;

    public ExpenseService(
            ExpenseEntryRepository expenses,
            ExpenseCategoryRepository categoryRepo,
            UserRepository users,
            CurrentUserService currentUser,
            @Value("${ledger.default-currency}") String defaultCurrency
    ) {
        this.expenses = expenses;
        this.categoryRepo = categoryRepo;
        this.users = users;
        this.currentUser = currentUser;
        this.defaultCurrency = defaultCurrency;
    }

    /* ── categories CRUD ── */

    @Transactional(readOnly = true)
    public List<ExpenseCategoryResponse> listCategories() {
        return categoryRepo.findByOwnerIdOrderByDisplayOrderAsc(currentUser.requireUserId())
                .stream()
                .map(c -> new ExpenseCategoryResponse(c.getId(), c.getName(), c.getDisplayOrder()))
                .toList();
    }

    @Transactional
    public ExpenseCategoryResponse createCategory(ExpenseCategoryRequest request) {
        AppUser owner = requireOwner();
        UUID ownerId = owner.getId();
        int order = categoryRepo.countByOwnerId(ownerId);
        ExpenseCategoryEntity entity = new ExpenseCategoryEntity(UUID.randomUUID(), owner, request.name().trim(), order);
        categoryRepo.save(entity);
        return new ExpenseCategoryResponse(entity.getId(), entity.getName(), entity.getDisplayOrder());
    }

    @Transactional
    public ExpenseCategoryResponse updateCategory(UUID id, ExpenseCategoryRequest request) {
        ExpenseCategoryEntity entity = requireOwnedCategory(id);
        entity.setName(request.name().trim());
        return new ExpenseCategoryResponse(entity.getId(), entity.getName(), entity.getDisplayOrder());
    }

    @Transactional
    public void deleteCategory(UUID id) {
        ExpenseCategoryEntity entity = requireOwnedCategory(id);
        categoryRepo.delete(entity);
    }

    /* ── expenses CRUD ── */

    @Transactional(readOnly = true)
    public List<ExpenseResponse> list(String from, String to) {
        YearMonth fromMonth = YearMonths.parse(from);
        YearMonth toMonth = YearMonths.parse(to);
        if (toMonth.isBefore(fromMonth)) {
            throw new IllegalArgumentException("to must be on or after from");
        }
        LocalDate fromDate = YearMonths.toFirstDay(fromMonth);
        LocalDate toDate = YearMonths.toFirstDay(toMonth);
        return expenses
                .findByOwnerIdAndOccurredMonthGreaterThanEqualAndOccurredMonthLessThanEqualOrderByOccurredMonthDescCreatedAtDesc(
                        currentUser.requireUserId(), fromDate, toDate)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public ExpenseResponse get(UUID id) {
        return toResponse(requireOwned(id));
    }

    @Transactional(readOnly = true)
    public ExpenseSummaryResponse summary(String from, String to) {
        YearMonth fromMonth = YearMonths.parse(from);
        YearMonth toMonth = YearMonths.parse(to);
        if (toMonth.isBefore(fromMonth)) {
            throw new IllegalArgumentException("to must be on or after from");
        }
        LocalDate fromDate = YearMonths.toFirstDay(fromMonth);
        LocalDate toDate = YearMonths.toFirstDay(toMonth);
        UUID ownerId = currentUser.requireUserId();

        List<Object[]> rows = expenses.sumByCategoryAndMonth(ownerId, fromDate, toDate);

        Map<String, Map<String, BigDecimal>> byCategoryByMonth = new LinkedHashMap<>();
        Map<String, BigDecimal> totalsByCategory = new LinkedHashMap<>();
        BigDecimal total = BigDecimal.ZERO;

        for (Object[] row : rows) {
            UUID catId = (UUID) row[0];
            String catName = (String) row[1];
            String month = (String) row[2];
            BigDecimal amount = (BigDecimal) row[3];

            byCategoryByMonth
                    .computeIfAbsent(catId.toString(), k -> new LinkedHashMap<>())
                    .put(month, amount);
            totalsByCategory.merge(catId.toString(), amount, BigDecimal::add);
            total = total.add(amount);
        }

        return new ExpenseSummaryResponse(byCategoryByMonth, totalsByCategory, total);
    }

    @Transactional
    public ExpenseResponse create(ExpenseRequest request) {
        AppUser owner = requireOwner();
        ExpenseCategoryEntity cat = requireOwnedCategory(request.categoryId());
        ExpenseEntry entry = new ExpenseEntry(
                UUID.randomUUID(),
                owner,
                cat,
                request.amount(),
                currency(request),
                YearMonths.toFirstDay(request.yearMonth()),
                Strings.trimToNull(request.note())
        );
        return toResponse(expenses.save(entry));
    }

    @Transactional
    public ExpenseResponse update(UUID id, ExpenseRequest request) {
        ExpenseEntry entry = requireOwned(id);
        ExpenseCategoryEntity cat = requireOwnedCategory(request.categoryId());
        entry.setCategory(cat);
        entry.setAmount(request.amount());
        entry.setCurrency(currency(request));
        entry.setOccurredMonth(YearMonths.toFirstDay(request.yearMonth()));
        entry.setNote(Strings.trimToNull(request.note()));
        return toResponse(entry);
    }

    @Transactional
    public void delete(UUID id) {
        expenses.delete(requireOwned(id));
    }

    @Transactional
    public int deleteByCategory(UUID categoryId, int year) {
        requireOwnedCategory(categoryId);
        LocalDate from = LocalDate.of(year, 1, 1);
        LocalDate to = LocalDate.of(year, 12, 1);
        long deleted = expenses.deleteByOwnerIdAndCategoryIdAndOccurredMonthGreaterThanEqualAndOccurredMonthLessThanEqual(
                currentUser.requireUserId(), categoryId, from, to);
        return (int) deleted;
    }

    /* ── internals ── */

    private String currency(ExpenseRequest request) {
        if (request.currency() == null || request.currency().isBlank()) {
            return defaultCurrency;
        }
        return request.currency().trim().toUpperCase();
    }

    private ExpenseEntry requireOwned(UUID id) {
        return expenses.findByIdAndOwnerId(id, currentUser.requireUserId())
                .orElseThrow(() -> new ResourceNotFoundException("ExpenseEntry", id));
    }

    private ExpenseCategoryEntity requireOwnedCategory(UUID id) {
        return categoryRepo.findById(id)
                .filter(c -> c.getOwner().getId().equals(currentUser.requireUserId()))
                .orElseThrow(() -> new ResourceNotFoundException("ExpenseCategory", id));
    }

    private AppUser requireOwner() {
        UUID ownerId = currentUser.requireUserId();
        return users.findById(ownerId).orElseThrow(() -> new ResourceNotFoundException("User", ownerId));
    }

    private ExpenseResponse toResponse(ExpenseEntry entry) {
        ExpenseCategoryEntity cat = entry.getCategory();
        return new ExpenseResponse(
                entry.getId(),
                cat.getId(),
                cat.getName(),
                entry.getAmount(),
                entry.getCurrency(),
                YearMonths.format(entry.getOccurredMonth()),
                entry.getNote(),
                entry.getCreatedAt(),
                entry.getUpdatedAt()
        );
    }
}
