package com.kaushiksridhar.finledger.rules;

public record CategoryRuleResponse(
        Long id,
        String pattern,
        RuleMatchType matchType,
        Long categoryId,
        String categoryName,
        String categoryColor) {

    public static CategoryRuleResponse from(CategoryRule rule) {
        return new CategoryRuleResponse(
                rule.getId(),
                rule.getPattern(),
                rule.getMatchType(),
                rule.getCategory().getId(),
                rule.getCategory().getName(),
                rule.getCategory().getColor());
    }
}
