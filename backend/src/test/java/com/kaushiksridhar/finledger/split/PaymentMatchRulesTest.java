package com.kaushiksridhar.finledger.split;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Plain unit tests: does a bank credit look like this friend paying you back? */
class PaymentMatchRulesTest {

    @Test
    @DisplayName("the first name must appear as a whole word, in any case")
    void nameAsWholeWord() {
        assertThat(PaymentMatchRules.mentions("UPI/CR/412345678901/ROHAN KULKARNI/okaxis/Goa", "Rohan Kulkarni", null)).isTrue();
        assertThat(PaymentMatchRules.mentions("NEFT CR-rohan k-HDFC", "Rohan", null)).isTrue();
        assertThat(PaymentMatchRules.mentions("UPI/CR/RAVINDRA SINGH", "Ravi", null)).isFalse();
        assertThat(PaymentMatchRules.mentions("GRAVITY FITNESS REFUND", "Ravi", null)).isFalse();
        assertThat(PaymentMatchRules.mentions("", "Rohan", null)).isFalse();
    }

    @Test
    @DisplayName("a UPI ID in the description counts even when the name doesn't appear")
    void upiId() {
        assertThat(PaymentMatchRules.mentions("UPI/CR/412345/nk.sharma@oksbi/trip", "Neha", "nk.sharma@oksbi")).isTrue();
        assertThat(PaymentMatchRules.mentions("UPI/CR/412345/someone@oksbi", "Neha", "nk.sharma@oksbi")).isFalse();
    }

    @Test
    @DisplayName("names shorter than three letters are skipped to avoid chance matches")
    void shortNames() {
        assertThat(PaymentMatchRules.firstName("Dr. Rao")).isEqualTo("Rao");
        assertThat(PaymentMatchRules.firstName("Al")).isNull();
        assertThat(PaymentMatchRules.mentions("AL AHLI BANK", "Al", null)).isFalse();
    }

    @Test
    @DisplayName("the amount can be off by up to a rupee")
    void amountTolerance() {
        assertThat(PaymentMatchRules.amountMatches(120_000, 120_000)).isTrue();
        assertThat(PaymentMatchRules.amountMatches(120_000, 119_950)).isTrue();
        assertThat(PaymentMatchRules.amountMatches(120_000, 119_899)).isFalse();
        assertThat(PaymentMatchRules.amountMatches(100, 0)).isFalse();
    }
}
