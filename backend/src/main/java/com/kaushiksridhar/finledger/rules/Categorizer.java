package com.kaushiksridhar.finledger.rules;

import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.kaushiksridhar.finledger.category.CategoryKind;
import com.kaushiksridhar.finledger.rules.BuiltInRules.BuiltInRule;

/**
 * Decides the category (and a tidy merchant name) for an imported transaction.
 *
 * 1. The user's own rules, oldest first. The first match wins, and it always applies.
 * 2. The built-in merchant rules, matched as whole words ("OLA" matches "OLA CABS" but not "COLA").
 *    A built-in match is skipped if it points the wrong way: an expense category on money coming in,
 *    or an income category on money going out ("CREDIT CARD INTEREST" charged to you isn't income).
 * 3. Otherwise the transaction stays uncategorised.
 *
 * Pure logic with no database access, so it's fast to unit test. CategorizerFactory builds one per import.
 */
public final class Categorizer {

    public record UserRule(RuleMatchType matchType, String pattern, long categoryId) {
    }

    public record CategoryRef(long id, CategoryKind kind) {
    }

    public record Result(Long categoryId, String merchant) {
        static final Result NONE = new Result(null, null);
    }

    private final List<UserRule> userRules;
    private final Map<String, CategoryRef> categoriesByName;

    public Categorizer(List<UserRule> userRules, Map<String, CategoryRef> categoriesByName) {
        this.userRules = List.copyOf(userRules);
        this.categoriesByName = Map.copyOf(categoriesByName);
    }

    public Result categorize(String description, long amountPaise) {
        String upper = description.toUpperCase(Locale.ROOT).trim();
        String words = " " + upper.replaceAll("[^A-Z0-9]+", " ").trim() + " ";

        BuiltInRule builtIn = firstBuiltInMatch(words, amountPaise);
        String merchant = builtIn != null ? builtIn.merchant() : null;

        for (UserRule rule : userRules) {
            boolean matches = switch (rule.matchType()) {
                case CONTAINS -> upper.contains(rule.pattern());
                case STARTS_WITH -> upper.startsWith(rule.pattern());
            };
            if (matches) {
                return new Result(rule.categoryId(), merchant);
            }
        }

        if (builtIn != null) {
            return new Result(categoriesByName.get(builtIn.category()).id(), merchant);
        }
        return Result.NONE;
    }

    private BuiltInRule firstBuiltInMatch(String words, long amountPaise) {
        for (BuiltInRule rule : BuiltInRules.RULES) {
            if (!words.contains(" " + rule.keyword() + " ")) {
                continue;
            }
            CategoryRef category = categoriesByName.get(rule.category());
            if (category == null) {
                continue;
            }
            boolean wrongWay = (category.kind() == CategoryKind.EXPENSE && amountPaise > 0)
                    || (category.kind() == CategoryKind.INCOME && amountPaise < 0);
            if (!wrongWay) {
                return rule;
            }
        }
        return null;
    }
}
