package com.kaushiksridhar.finledger.investment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.kaushiksridhar.finledger.common.AppTime;
import com.kaushiksridhar.finledger.support.ApiTestClient;
import com.kaushiksridhar.finledger.support.FakeNavSource;

/**
 * Mutual funds through the real API, with FakeNavSource instead of mfapi.in.
 * The "Steady Growth" fund's NAV is 100 until 180 days ago and 120 after, so the numbers are easy to check.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class InvestmentFlowTest {

    private static final int STEADY = FakeNavSource.STEADY_GROWTH;
    private static final String STEADY_NAME = "Steady Growth Fund - Direct Plan - Growth";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ApiTestClient api;
    private String arjun;
    private long hdfc;
    private final LocalDate today = AppTime.today(Clock.systemUTC());

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update("DELETE FROM mf_schemes");   // cached NAVs are relative to the day the tests run
        api = new ApiTestClient(mockMvc);
        arjun = api.signUp("arjun@example.com");
        hdfc = api.createAccount(arjun, "HDFC Savings");
    }

    @Test
    @DisplayName("funds are searched by name, with at least 3 letters")
    void search() throws Exception {
        List<Number> codes = ApiTestClient.read(api.body(arjun, "/api/investments/search?q=parag"), "$[*].schemeCode");
        assertThat(codes).extracting(Number::intValue).containsExactly(122639);

        mockMvc.perform(get("/api/investments/search?q=pa").header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("a purchase is valued at the latest NAV, with its gain and a yearly return of about 20%")
    void purchaseValueAndXirr() throws Exception {
        String portfolio = trade(STEADY, "BUY", today.minusDays(365), 1_000_000, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Rs 10,000 at NAV 100 is 100 units, now worth Rs 12,000 at NAV 120
        assertThat(ApiTestClient.number(portfolio, "$.funds[0].units")).isEqualTo(100);
        assertThat((String) ApiTestClient.read(portfolio, "$.funds[0].name")).isEqualTo(STEADY_NAME);
        assertThat(ApiTestClient.number(portfolio, "$.investedPaise")).isEqualTo(1_000_000);
        assertThat(ApiTestClient.number(portfolio, "$.valuePaise")).isEqualTo(1_200_000);
        assertThat(ApiTestClient.number(portfolio, "$.gainPaise")).isEqualTo(200_000);

        Number xirr = ApiTestClient.read(portfolio, "$.xirr");
        assertThat(xirr.doubleValue()).isBetween(0.19, 0.21);

        List<Number> values = ApiTestClient.read(portfolio, "$.history[*].valuePaise");
        assertThat(values.get(values.size() - 1).longValue()).isEqualTo(1_200_000);
        assertThat(ApiTestClient.<Boolean>read(portfolio, "$.transactions[0].fromBank")).isFalse();
    }

    @Test
    @DisplayName("a redemption removes the average cost of the units sold, and you can't sell more than you hold")
    void redemption() throws Exception {
        trade(STEADY, "BUY", today.minusDays(365), 1_000_000, null).andExpect(status().isCreated());

        // Rs 6,000 at NAV 120 is 50 units, half the holding
        String portfolio = trade(STEADY, "SELL", today.minusDays(10), 600_000, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        assertThat(ApiTestClient.number(portfolio, "$.funds[0].units")).isEqualTo(50);
        assertThat(ApiTestClient.number(portfolio, "$.investedPaise")).isEqualTo(500_000);
        assertThat(ApiTestClient.number(portfolio, "$.valuePaise")).isEqualTo(600_000);

        String oversold = trade(STEADY, "SELL", today.minusDays(3), 720_000, null)
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) ApiTestClient.read(oversold, "$.detail")).startsWith("You only held 50.000 units of " + STEADY_NAME);

        // A sale dated before the purchase it needs is refused too, even though you hold enough units today
        trade(STEADY, "SELL", today.minusDays(400), 100_000, null)
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("future dates, dates before the fund's history, and missing fields are refused")
    void invalidTrades() throws Exception {
        trade(STEADY, "BUY", today.plusDays(1), 100_000, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The date can't be in the future"));

        String noNav = trade(STEADY, "BUY", today.minusDays(900), 100_000, null)
                .andExpect(status().isBadRequest())
                .andReturn().getResponse().getContentAsString();
        assertThat((String) ApiTestClient.read(noNav, "$.detail")).startsWith("There's no NAV for " + STEADY_NAME);

        mockMvc.perform(post("/api/investments/transactions")
                .header(HttpHeaders.AUTHORIZATION, arjun)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"type": "BUY", "date": "%s", "amountPaise": 0}
                        """.formatted(today)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.schemeCode").exists())
                .andExpect(jsonPath("$.errors.amountPaise").exists());
    }

    @Test
    @DisplayName("if mfapi.in can't be reached, adding a new fund fails with 503 and a clear message")
    void sourceDown() throws Exception {
        trade(FakeNavSource.UNREACHABLE, "BUY", today.minusDays(30), 100_000, null)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value(
                        "Couldn't get fund prices from mfapi.in right now. Please try again in a minute."));
    }

    @Test
    @DisplayName("SIP debits are suggested, linking turns every debit into a purchase, and it follows the ledger")
    void sipFromBankStatement() throws Exception {
        for (int daysAgo : new int[] { 95, 65, 35 }) {
            api.addTransaction(arjun, hdfc, null, -500_000, today.minusDays(daysAgo),
                    "NACH/BSE STARMF/SIP STEADY GROWTH/4281" + daysAgo + "1234", "Steady MF");
        }
        api.addTransaction(arjun, hdfc, null, -45_000, today.minusDays(20), "Swiggy order", "Swiggy");

        String suggestions = api.body(arjun, "/api/investments/sip-suggestions");
        assertThat(ApiTestClient.number(suggestions, "$.length()")).isEqualTo(1);
        assertThat(ApiTestClient.number(suggestions, "$[0].occurrences")).isEqualTo(3);
        assertThat(ApiTestClient.number(suggestions, "$[0].typicalAmountPaise")).isEqualTo(500_000);
        assertThat((String) ApiTestClient.read(suggestions, "$[0].suggestedQuery")).isEqualTo("Steady Growth");
        String matchKey = ApiTestClient.read(suggestions, "$[0].matchKey");

        String linked = mockMvc.perform(post("/api/investments/sip-links")
                .header(HttpHeaders.AUTHORIZATION, arjun)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"matchKey": "%s", "schemeCode": %d}
                        """.formatted(matchKey, STEADY)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        // Three Rs 5,000 purchases, all at NAV 120
        assertThat(ApiTestClient.number(linked, "$.transactions.length()")).isEqualTo(3);
        assertThat(ApiTestClient.<List<Boolean>>read(linked, "$.transactions[*].fromBank")).containsOnly(true);
        assertThat(ApiTestClient.number(linked, "$.investedPaise")).isEqualTo(1_500_000);
        assertThat(ApiTestClient.number(linked, "$.sips[0].purchases")).isEqualTo(3);
        assertThat(ApiTestClient.<Boolean>read(linked, "$.funds[0].sip")).isTrue();
        assertThat(ApiTestClient.number(api.body(arjun, "/api/investments/sip-suggestions"), "$.length()")).isZero();

        // A new debit in the ledger becomes a purchase by itself...
        api.addTransaction(arjun, hdfc, null, -500_000, today.minusDays(5),
                "NACH/BSE STARMF/SIP STEADY GROWTH/428199999", "Steady MF");
        assertThat(ApiTestClient.number(portfolio(), "$.transactions.length()")).isEqualTo(4);

        // ...and deleting it from the ledger removes the purchase
        Number newest = ApiTestClient.read(api.body(arjun, "/api/transactions?size=1"), "$.items[0].id");
        mockMvc.perform(delete("/api/transactions/" + newest).header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isNoContent());
        String afterDelete = portfolio();
        assertThat(ApiTestClient.number(afterDelete, "$.transactions.length()")).isEqualTo(3);

        // Bank purchases can't be deleted one by one; unlinking the SIP removes them all
        Number purchaseId = ApiTestClient.read(afterDelete, "$.transactions[0].id");
        mockMvc.perform(delete("/api/investments/transactions/" + purchaseId).header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isBadRequest());

        Number sipId = ApiTestClient.read(afterDelete, "$.sips[0].id");
        String unlinked = mockMvc.perform(delete("/api/investments/sip-links/" + sipId).header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(ApiTestClient.number(unlinked, "$.transactions.length()")).isZero();
        assertThat(ApiTestClient.number(unlinked, "$.funds.length()")).isZero();
    }

    @Test
    @DisplayName("nobody can see or delete someone else's fund transactions")
    void privacy() throws Exception {
        String portfolio = trade(STEADY, "BUY", today.minusDays(100), 100_000, null).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Number id = ApiTestClient.read(portfolio, "$.transactions[0].id");

        String priya = api.signUp("priya@example.com");
        mockMvc.perform(delete("/api/investments/transactions/" + id).header(HttpHeaders.AUTHORIZATION, priya))
                .andExpect(status().isNotFound());
        assertThat(ApiTestClient.number(api.body(priya, "/api/investments"), "$.transactions.length()")).isZero();
        assertThat(ApiTestClient.number(portfolio(), "$.transactions.length()")).isEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions trade(int schemeCode, String type, LocalDate date, long amountPaise, String units) throws Exception {
        return mockMvc.perform(post("/api/investments/transactions")
                .header(HttpHeaders.AUTHORIZATION, arjun)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"schemeCode": %d, "type": "%s", "date": "%s", "amountPaise": %d, "units": %s}
                        """.formatted(schemeCode, type, date, amountPaise, units == null ? "null" : units)));
    }

    private String portfolio() throws Exception {
        return api.body(arjun, "/api/investments");
    }
}
