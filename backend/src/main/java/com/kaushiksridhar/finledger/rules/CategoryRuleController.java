package com.kaushiksridhar.finledger.rules;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.kaushiksridhar.finledger.security.CurrentUser;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/category-rules")
public class CategoryRuleController {

    private final CategoryRuleService ruleService;

    public CategoryRuleController(CategoryRuleService ruleService) {
        this.ruleService = ruleService;
    }

    @GetMapping
    public List<CategoryRuleResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return ruleService.list(CurrentUser.id(jwt));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CategoryRuleResponse create(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody CategoryRuleRequest request) {
        return ruleService.create(CurrentUser.id(jwt), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable("id") long id) {
        ruleService.delete(CurrentUser.id(jwt), id);
    }
}
