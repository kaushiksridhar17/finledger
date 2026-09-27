package com.kaushiksridhar.finledger.recurring;

import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kaushiksridhar.finledger.security.CurrentUser;

@RestController
@RequestMapping("/api/recurring")
public class RecurringController {

    private final RecurringService recurringService;

    public RecurringController(RecurringService recurringService) {
        this.recurringService = recurringService;
    }

    @GetMapping
    public RecurringOverview overview(@AuthenticationPrincipal Jwt jwt) {
        return recurringService.overview(CurrentUser.id(jwt));
    }

    /** Confirmed bills due in the next N days (default 14), for the dashboard. */
    @GetMapping("/upcoming")
    public List<RecurringPaymentResponse> upcoming(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "days", defaultValue = "14") int days) {
        return recurringService.upcoming(CurrentUser.id(jwt), Math.clamp(days, 1, 60));
    }

    /** Run detection now instead of waiting for the nightly job. */
    @PostMapping("/scan")
    public ScanResult scan(@AuthenticationPrincipal Jwt jwt) {
        return recurringService.scan(CurrentUser.id(jwt));
    }

    @PostMapping("/{id}/confirm")
    public RecurringPaymentResponse confirm(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") long id) {
        return recurringService.setStatus(CurrentUser.id(jwt), id, RecurringStatus.CONFIRMED);
    }

    @PostMapping("/{id}/dismiss")
    public RecurringPaymentResponse dismiss(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") long id) {
        return recurringService.setStatus(CurrentUser.id(jwt), id, RecurringStatus.DISMISSED);
    }
}
