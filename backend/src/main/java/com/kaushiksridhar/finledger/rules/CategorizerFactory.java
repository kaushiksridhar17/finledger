package com.kaushiksridhar.finledger.rules;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.kaushiksridhar.finledger.category.Category;
import com.kaushiksridhar.finledger.category.CategoryRepository;
import com.kaushiksridhar.finledger.rules.Categorizer.CategoryRef;
import com.kaushiksridhar.finledger.rules.Categorizer.UserRule;

/** Loads a user's rules and categories once, so a whole import is categorised without further queries. */
@Component
public class CategorizerFactory {

    private final CategoryRuleRepository ruleRepository;
    private final CategoryRepository categoryRepository;

    public CategorizerFactory(CategoryRuleRepository ruleRepository, CategoryRepository categoryRepository) {
        this.ruleRepository = ruleRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public Categorizer forUser(long userId) {
        List<UserRule> rules = ruleRepository.findByUserIdOrderByIdAsc(userId).stream()
                .map(rule -> new UserRule(rule.getMatchType(), rule.getPattern(), rule.getCategory().getId()))
                .toList();

        Map<String, CategoryRef> categories = categoryRepository.findVisibleTo(userId).stream()
                .collect(Collectors.toMap(Category::getName, c -> new CategoryRef(c.getId(), c.getKind())));

        return new Categorizer(rules, categories);
    }
}
