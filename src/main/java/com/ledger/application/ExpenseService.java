package com.ledger.application;

import com.ledger.api.dto.ExpenseCategoryResponse;
import com.ledger.api.dto.ExpenseRequest;
import com.ledger.api.dto.ExpenseResponse;
import com.ledger.domain.AppUser;
import com.ledger.domain.ExpenseCategory;
import com.ledger.domain.ExpenseEntry;
import com.ledger.infrastructure.persistence.ExpenseEntryRepository;
import com.ledger.infrastructure.persistence.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Service
public class ExpenseService {

    private final ExpenseEntryRepository expenses;
    private final UserRepository users;
    private final CurrentUserService currentUser;
    private final String defaultCurrency;

    public ExpenseService(
            ExpenseEntryRepository expenses,
            UserRepository users,
            CurrentUserService currentUser,
            @Value("${ledger.default-currency}") String defaultCurrency
    ) {
        this.expenses = expenses;
        this.users = users;
        this.currentUser = currentUser;
        this.defaultCurrency = defaultCurrency;
    }

    @Transactional(readOnly = true)
    public List<ExpenseCategoryResponse> categories() {
        return Arrays.stream(ExpenseCategory.values())
                .map(category -> new ExpenseCategoryResponse(category, category.getLabel()))
                .toList();
    }

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

    @Transactional
    public ExpenseResponse create(ExpenseRequest request) {
        AppUser owner = requireOwner();
        ExpenseEntry entry = new ExpenseEntry(
                UUID.randomUUID(),
                owner,
                request.category(),
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
        entry.setCategory(request.category());
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

    private AppUser requireOwner() {
        UUID ownerId = currentUser.requireUserId();
        return users.findById(ownerId).orElseThrow(() -> new ResourceNotFoundException("User", ownerId));
    }

    private ExpenseResponse toResponse(ExpenseEntry entry) {
        return new ExpenseResponse(
                entry.getId(),
                entry.getCategory(),
                entry.getAmount(),
                entry.getCurrency(),
                YearMonths.format(entry.getOccurredMonth()),
                entry.getNote(),
                entry.getCreatedAt(),
                entry.getUpdatedAt()
        );
    }
}
