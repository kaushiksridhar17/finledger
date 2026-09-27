package com.kaushiksridhar.finledger.split;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RenameGroupRequest(

        @NotBlank(message = "Give the group a name")
        @Size(max = 80, message = "Keep the name under 80 characters")
        String name) {
}
