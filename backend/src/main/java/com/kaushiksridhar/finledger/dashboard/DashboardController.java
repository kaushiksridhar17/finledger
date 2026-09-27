package com.kaushiksridhar.finledger.dashboard;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.kaushiksridhar.finledger.common.ApiException;
import com.kaushiksridhar.finledger.common.AppTime;
import com.kaushiksridhar.finledger.security.CurrentUser;

@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final DashboardService dashboardService;
    private final Clock clock;

    public DashboardController(DashboardService dashboardService, Clock clock) {
        this.dashboardService = dashboardService;
        this.clock = clock;
    }

    /** GET /api/dashboard?month=2026-09. Without a month, the current month in Indian time. */
    @GetMapping
    public DashboardResponse get(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "month", required = false) String month) {

        return dashboardService.get(CurrentUser.id(jwt), parseMonth(month));
    }

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.from(LocalDate.ofInstant(clock.instant(), AppTime.ZONE));
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (DateTimeParseException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "month must look like 2026-09");
        }
    }
}
