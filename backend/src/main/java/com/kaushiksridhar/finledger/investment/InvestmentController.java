package com.kaushiksridhar.finledger.investment;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.kaushiksridhar.finledger.common.ApiException;
import com.kaushiksridhar.finledger.security.CurrentUser;

import jakarta.validation.Valid;

/**
 * Mutual funds. Changes return the whole updated portfolio, so the page redraws from one response.
 * Anything that needs a fund's NAVs fetches them first, outside any database transaction.
 */
@RestController
@RequestMapping("/api/investments")
public class InvestmentController {

    private final InvestmentService investmentService;
    private final SchemeCache schemeCache;

    public InvestmentController(InvestmentService investmentService, SchemeCache schemeCache) {
        this.investmentService = investmentService;
        this.schemeCache = schemeCache;
    }

    @GetMapping
    public PortfolioResponse portfolio(@AuthenticationPrincipal Jwt jwt) {
        return investmentService.portfolio(CurrentUser.id(jwt));
    }

    /** GET /api/investments/search?q=parag flexi */
    @GetMapping("/search")
    public List<SchemeHit> search(@RequestParam(name = "q", defaultValue = "") String query) {
        String trimmed = query.trim();
        if (trimmed.length() < 3) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Type at least 3 letters of the fund's name");
        }
        return schemeCache.search(trimmed);
    }

    @PostMapping("/transactions")
    @ResponseStatus(HttpStatus.CREATED)
    public PortfolioResponse addTransaction(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody FundTransactionRequest request) {
        long userId = CurrentUser.id(jwt);
        schemeCache.ensure(request.schemeCode());
        investmentService.addTransaction(userId, request);
        return investmentService.portfolio(userId);
    }

    @DeleteMapping("/transactions/{id}")
    public PortfolioResponse deleteTransaction(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") long id) {
        long userId = CurrentUser.id(jwt);
        investmentService.deleteTransaction(userId, id);
        return investmentService.portfolio(userId);
    }

    @GetMapping("/sip-suggestions")
    public List<SipSuggestion> sipSuggestions(@AuthenticationPrincipal Jwt jwt) {
        return investmentService.sipSuggestions(CurrentUser.id(jwt));
    }

    @PostMapping("/sip-links")
    @ResponseStatus(HttpStatus.CREATED)
    public PortfolioResponse linkSip(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody SipLinkRequest request) {
        long userId = CurrentUser.id(jwt);
        schemeCache.ensure(request.schemeCode());
        investmentService.linkSip(userId, request);
        return investmentService.portfolio(userId);
    }

    @DeleteMapping("/sip-links/{id}")
    public PortfolioResponse unlinkSip(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") long id) {
        long userId = CurrentUser.id(jwt);
        investmentService.unlinkSip(userId, id);
        return investmentService.portfolio(userId);
    }

    /**
     * Fetches the latest NAVs for your funds now, instead of waiting for the nightly refresh.
     * Funds that can't be fetched keep their last NAV; it's only an error if none could be.
     */
    @PostMapping("/refresh")
    public PortfolioResponse refresh(@AuthenticationPrincipal Jwt jwt) {
        long userId = CurrentUser.id(jwt);
        PortfolioResponse current = investmentService.portfolio(userId);
        Set<Integer> codes = new LinkedHashSet<>();
        current.funds().forEach(fund -> codes.add(fund.schemeCode()));
        current.sips().forEach(sip -> codes.add(sip.schemeCode()));

        ApiException lastFailure = null;
        int refreshed = 0;
        for (Integer code : codes) {
            try {
                schemeCache.refresh(code);
                refreshed++;
            } catch (ApiException e) {
                lastFailure = e;
            }
        }
        investmentService.syncSips(userId);
        if (refreshed == 0 && lastFailure != null) {
            throw lastFailure;
        }
        return investmentService.portfolio(userId);
    }
}
