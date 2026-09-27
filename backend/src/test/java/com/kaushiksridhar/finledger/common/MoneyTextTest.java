package com.kaushiksridhar.finledger.common;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class MoneyTextTest {

    private static final String RUPEE = "\u20B9";

    @Test
    @DisplayName("uses Indian grouping: thousands, then lakhs and crores in pairs")
    void indianGrouping() {
        assertThat(MoneyText.format(64_900)).isEqualTo(RUPEE + "649");
        assertThat(MoneyText.format(510_000)).isEqualTo(RUPEE + "5,100");
        assertThat(MoneyText.format(12_345_650)).isEqualTo(RUPEE + "1,23,456.50");
        assertThat(MoneyText.format(100_000_000_000L)).isEqualTo(RUPEE + "1,00,00,00,000");
    }

    @Test
    @DisplayName("shows paise only when there are some, and keeps the minus sign")
    void paiseAndSign() {
        assertThat(MoneyText.format(5)).isEqualTo(RUPEE + "0.05");
        assertThat(MoneyText.format(-2_075)).isEqualTo("-" + RUPEE + "20.75");
        assertThat(MoneyText.format(0)).isEqualTo(RUPEE + "0");
    }
}
