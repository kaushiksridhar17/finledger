package com.kaushiksridhar.finledger.transaction;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
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

import com.kaushiksridhar.finledger.common.PageResponse;
import com.kaushiksridhar.finledger.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/transactions")
public class TransactionController {

    private final TransactionService transactionService;

    public TransactionController(TransactionService transactionService) {
        this.transactionService = transactionService;
    }

    /** e.g. GET /api/transactions?accountId=3&direction=OUT&from=2026-09-01&to=2026-09-30&q=swiggy&page=0&size=25 */
    @GetMapping
    public PageResponse<TransactionResponse> list(
            @AuthenticationPrincipal Jwt jwt,
            @RequestParam(name = "accountId", required = false) Long accountId,
            @RequestParam(name = "categoryId", required = false) Long categoryId,
            @RequestParam(name = "from", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(name = "to", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(name = "direction", required = false) String direction,
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "25") int size) {

        TransactionFilter filter = new TransactionFilter(accountId, categoryId, from, to, direction, q);
        return transactionService.search(CurrentUser.id(jwt), filter, page, size);
    }

    @GetMapping("/{id}")
    public TransactionResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") long id) {
        return transactionService.get(CurrentUser.id(jwt), id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody TransactionRequest request) {
        return transactionService.create(CurrentUser.id(jwt), request);
    }

    @PutMapping("/{id}")
    public TransactionResponse update(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") long id,
            @Valid @RequestBody TransactionRequest request) {
        return transactionService.update(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") long id) {
        transactionService.delete(CurrentUser.id(jwt), id);
    }
}
