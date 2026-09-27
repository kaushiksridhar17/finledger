package com.kaushiksridhar.finledger.split;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** A friend in a group. The UPI ID is optional; with one, the app can show a "Pay with UPI" link. */
public record MemberRequest(

        @NotBlank(message = "Enter a name")
        @Size(max = 100, message = "Keep the name under 100 characters")
        String name,

        @Size(max = 100, message = "That UPI ID is too long")
        @Pattern(regexp = MemberRequest.UPI_PATTERN, message = "That doesn't look like a UPI ID, e.g. name@okaxis")
        String upiId) {

    /** Empty, or something@handle: letters, digits, dots, dashes and underscores, then @ and the bank's handle. */
    public static final String UPI_PATTERN = "^$|^[A-Za-z0-9._-]{2,}@[A-Za-z][A-Za-z0-9]{1,63}$";
}
