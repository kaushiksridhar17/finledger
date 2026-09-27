package com.kaushiksridhar.finledger.rules;

import java.util.List;
import java.util.Locale;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kaushiksridhar.finledger.category.CategoryService;
import com.kaushiksridhar.finledger.common.ApiException;
import com.kaushiksridhar.finledger.user.UserRepository;

@Service
public class CategoryRuleService {

    static final int MAX_RULES = 200;

    private final CategoryRuleRepository ruleRepository;
    private final CategoryService categoryService;
    private final UserRepository userRepository;

    public CategoryRuleService(CategoryRuleRepository ruleRepository,
            CategoryService categoryService,
            UserRepository userRepository) {
        this.ruleRepository = ruleRepository;
        this.categoryService = categoryService;
        this.userRepository = userRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryRuleResponse> list(long userId) {
        return ruleRepository.findByUserIdOrderByIdAsc(userId).stream()
                .map(CategoryRuleResponse::from)
                .toList();
    }

    @Transactional
    public CategoryRuleResponse create(long userId, CategoryRuleRequest request) {
        if (ruleRepository.findByUserIdOrderByIdAsc(userId).size() >= MAX_RULES) {
            throw new ApiException(HttpStatus.CONFLICT, "You can have at most " + MAX_RULES + " rules");
        }

        CategoryRule rule = new CategoryRule();
        rule.setUser(userRepository.getReferenceById(userId));
        rule.setCategory(categoryService.getVisible(userId, request.categoryId()));
        rule.setMatchType(request.matchType());
        rule.setPattern(request.pattern().trim().replaceAll("\\s+", " ").toUpperCase(Locale.ROOT));

        return CategoryRuleResponse.from(ruleRepository.save(rule));
    }

    @Transactional
    public void delete(long userId, long ruleId) {
        CategoryRule rule = ruleRepository.findByIdAndUserId(ruleId, userId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Rule not found"));
        ruleRepository.delete(rule);
    }
}
