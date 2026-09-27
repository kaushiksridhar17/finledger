package com.kaushiksridhar.finledger.category;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record CategoryRequest(

        @NotBlank(message = "Name is required")
        @Size(max = 50, message = "Name must be at most 50 characters")
        String name,

        @NotNull(message = "Kind is required")
        CategoryKind kind,

        @NotBlank(message = "Colour is required")
        @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "Colour must look like #1a2b3c")
        String color) {
}
