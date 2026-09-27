package com.kaushiksridhar.finledger.investment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** "The debits with this key are my SIP in this fund." matchKey comes from a SIP suggestion. */
public record SipLinkRequest(

        @NotBlank(message = "Choose which SIP to link")
        @Size(max = 120)
        String matchKey,

        @NotNull(message = "Choose a fund")
        Integer schemeCode) {
}
