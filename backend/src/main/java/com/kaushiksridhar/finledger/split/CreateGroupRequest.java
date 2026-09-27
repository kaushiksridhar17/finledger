package com.kaushiksridhar.finledger.split;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** A new group: its name and the friends in it. The user is added automatically. */
public record CreateGroupRequest(

        @NotBlank(message = "Give the group a name")
        @Size(max = 80, message = "Keep the name under 80 characters")
        String name,

        @NotNull(message = "Add at least one friend")
        @Size(min = 1, max = 20, message = "Add between 1 and 20 friends")
        List<@Valid @NotNull MemberRequest> friends) {
}
