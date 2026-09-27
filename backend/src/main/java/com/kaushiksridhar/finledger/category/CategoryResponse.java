package com.kaushiksridhar.finledger.category;

public record CategoryResponse(
        Long id,
        String name,
        CategoryKind kind,
        String color,
        boolean system) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getKind(),
                category.getColor(),
                category.isSystem());
    }
}
