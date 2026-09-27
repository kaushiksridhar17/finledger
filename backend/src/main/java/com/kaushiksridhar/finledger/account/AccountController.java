package com.kaushiksridhar.finledger.account;

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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.kaushiksridhar.finledger.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/accounts")
public class AccountController {

    private final AccountService accountService;

    public AccountController(AccountService accountService) {
        this.accountService = accountService;
    }

    @GetMapping
    public List<AccountResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return accountService.list(CurrentUser.id(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody AccountRequest request) {
        return accountService.create(CurrentUser.id(jwt), request);
    }

    @PutMapping("/{id}")
    public AccountResponse update(@AuthenticationPrincipal Jwt jwt,
            @PathVariable("id") long id,
            @Valid @RequestBody AccountRequest request) {
        return accountService.update(CurrentUser.id(jwt), id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") long id) {
        accountService.delete(CurrentUser.id(jwt), id);
    }
}
