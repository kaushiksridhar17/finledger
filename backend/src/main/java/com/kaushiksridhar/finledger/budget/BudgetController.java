package com.kaushiksridhar.finledger.budget;

import java.time.Clock;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
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

import com.kaushiksridhar.finledger.common.ApiException;
import com.kaushiksridhar.finledger.common.AppTime;
import com.kaushiksridhar.finledger.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/budgets")
public class BudgetController {

    private final BudgetService budgetService;
    private final BudgetAlertService alertService;
    private final Clock clock;

    public BudgetController(BudgetService budgetService, BudgetAlertService alertService, Clock clock) {
        this.budgetService = budgetService;
        this.alertService = alertService;
        this.clock = clock;
    }

    /** GET /api/budgets?month=2026-09. Without a month, this month. */
    @GetMapping
    public List<BudgetResponse> list(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "month", required = false) String month) {
        return budgetService.progress(CurrentUser.id(jwt), parseMonth(month));
    }

    @GetMapping("/suggestions")
    public List<BudgetSuggestion> suggestions(@AuthenticationPrincipal Jwt jwt) {
        return budgetService.suggestions(CurrentUser.id(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BudgetResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody BudgetRequest request) {
        long userId = CurrentUser.id(jwt);
        BudgetResponse created = budgetService.create(userId, request);
        alertService.checkCurrentMonth(userId);    // a new, low limit may already be crossed
        return created;
    }

    @PutMapping("/{id}")
    public BudgetResponse update(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") long id,
            @Valid @RequestBody BudgetLimitRequest request) {
        long userId = CurrentUser.id(jwt);
        BudgetResponse updated = budgetService.updateLimit(userId, id, request);
        alertService.checkCurrentMonth(userId);
        return updated;
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") long id) {
        budgetService.delete(CurrentUser.id(jwt), id);
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return AppTime.thisMonth(clock);
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "month must look like 2026-09");
        }
    }
}
