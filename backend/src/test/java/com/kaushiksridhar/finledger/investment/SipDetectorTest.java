package com.kaushiksridhar.finledger.investment;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** Plain unit tests of spotting SIP debits and guessing the fund. */
class SipDetectorTest {

    @Test
    @DisplayName("SIP debits are recognised by their wording or by the Investment category")
    void recognises() {
        assertThat(SipDetector.looksLikeSip("NACH/BSE STARMF/SIP PARAG PARIKH FLEXI CAP", "PPFAS Mutual Fund", null)).isTrue();
        assertThat(SipDetector.looksLikeSip("ACH D- KFINTECH MIRAE ASSET 55512", null, null)).isTrue();
        assertThat(SipDetector.looksLikeSip("ZERODHA COIN 8841", null, "Investment")).isTrue();
        assertThat(SipDetector.looksLikeSip("SWIGGY ORDER 1234", "Swiggy", "Food & Dining")).isFalse();
        assertThat(SipDetector.looksLikeSip("GOSSIP CAFE", null, null)).isFalse();
    }

    @Test
    @DisplayName("each month's debit gets the same key, whatever its reference number")
    void stableKey() {
        String august = SipDetector.key("NACH/BSE STARMF/SIP PARAG PARIKH FLEXI CAP/428112345673");
        String september = SipDetector.key("NACH/BSE STARMF/SIP PARAG PARIKH FLEXI CAP/428212399999");
        assertThat(august).isEqualTo("NACH BSE STARMF SIP PARAG PARIKH FLEXI CAP").isEqualTo(september);
    }

    @Test
    @DisplayName("the fund name is guessed from the words after SIP, or from the merchant")
    void fundQuery() {
        assertThat(SipDetector.fundQuery("NACH/BSE STARMF/SIP PARAG PARIKH FLEXI CAP", null)).isEqualTo("Parag Parikh Flexi Cap");
        assertThat(SipDetector.fundQuery("ACH DR 00123 KFINTECH", "Mirae Asset Mutual Fund")).isEqualTo("Mirae Asset");
    }
}
