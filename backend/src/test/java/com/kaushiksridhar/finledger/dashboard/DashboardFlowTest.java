package com.kaushiksridhar.finledger.dashboard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

/**
 * Checks the dashboard numbers against a small, hand-calculated set of transactions.
 * The key rule: transfers (like an SIP) are neither income nor spending.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class DashboardFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String arjun;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM users");
        arjun = signUp("arjun@example.com");

        long hdfc = createAccount(arjun, "HDFC Savings", 1_000_000);
        long salary = categoryId("Salary");
        long food = categoryId("Food & Dining");
        long rent = categoryId("Rent");
        long investment = categoryId("Investment");
        long shopping = categoryId("Shopping");

        // September 2026
        add(hdfc, salary, 5_000_000, "2026-09-01", "September salary");
        add(hdfc, food, -100_000, "2026-09-03", "Swiggy");
        add(hdfc, rent, -2_000_000, "2026-09-05", "Rent");
        add(hdfc, food, -50_000, "2026-09-10", "Zomato");
        add(hdfc, investment, -500_000, "2026-09-10", "SIP");          // transfer: not spending
        add(hdfc, null, -30_000, "2026-09-12", "Something");           // uncategorised spending

        // August 2026
        add(hdfc, salary, 5_000_000, "2026-08-01", "August salary");
        add(hdfc, food, -200_000, "2026-08-20", "Dinner out");

        // September 2025: 13 months back, outside the 12-month window
        add(hdfc, shopping, -99_900, "2025-09-15", "Old purchase");
    }

    @Test
    @DisplayName("income and spending for the month leave transfers out")
    void monthTotals() throws Exception {
        mockMvc.perform(get("/api/dashboard?month=2026-09").header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.month").value("2026-09"))
                .andExpect(jsonPath("$.incomePaise").value(5_000_000))
                .andExpect(jsonPath("$.spendingPaise").value(100_000 + 2_000_000 + 50_000 + 30_000));
    }

    @Test
    @DisplayName("the monthly history has exactly 12 months, oldest first, with zeros for empty months")
    void twelveMonthHistory() throws Exception {
        String json = dashboard("2026-09");

        List<String> months = JsonPath.read(json, "$.monthly[*].month");
        assertThat(months).hasSize(12);
        assertThat(months.get(0)).isEqualTo("2025-10");
        assertThat(months.get(11)).isEqualTo("2026-09");

        Number augustSpending = JsonPath.read(json, "$.monthly[10].spendingPaise");
        Number augustIncome = JsonPath.read(json, "$.monthly[10].incomePaise");
        Number octoberSpending = JsonPath.read(json, "$.monthly[0].spendingPaise");
        assertThat(augustSpending.longValue()).isEqualTo(200_000);
        assertThat(augustIncome.longValue()).isEqualTo(5_000_000);
        assertThat(octoberSpending.longValue()).isZero();
    }

    @Test
    @DisplayName("spending by category is biggest first, includes uncategorised, and leaves out transfers")
    void spendingByCategory() throws Exception {
        String json = dashboard("2026-09");

        List<String> names = JsonPath.read(json, "$.spendingByCategory[*].name");
        List<Number> amounts = JsonPath.read(json, "$.spendingByCategory[*].amountPaise");

        assertThat(names).containsExactly("Rent", "Food & Dining", "Uncategorised");
        assertThat(amounts.stream().map(Number::longValue).toList()).containsExactly(2_000_000L, 150_000L, 30_000L);
    }

    @Test
    @DisplayName("net worth counts every transaction, transfers included, on top of the opening balance")
    void netWorth() throws Exception {
        long expected = 1_000_000
                + 5_000_000 - 100_000 - 2_000_000 - 50_000 - 500_000 - 30_000
                + 5_000_000 - 200_000
                - 99_900;

        Number netWorth = JsonPath.read(dashboard("2026-09"), "$.netWorthPaise");
        assertThat(netWorth.longValue()).isEqualTo(expected);
    }

    @Test
    @DisplayName("recent shows the five newest transactions, newest first")
    void recentTransactions() throws Exception {
        List<String> recent = JsonPath.read(dashboard("2026-09"), "$.recent[*].description");
        assertThat(recent).hasSize(5);
        assertThat(recent.get(0)).isEqualTo("Something");
    }

    @Test
    @DisplayName("another user's dashboard is empty, and a malformed month is rejected")
    void privacyAndValidation() throws Exception {
        String priya = signUp("priya@example.com");

        mockMvc.perform(get("/api/dashboard?month=2026-09").header(HttpHeaders.AUTHORIZATION, priya))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.spendingPaise").value(0))
                .andExpect(jsonPath("$.netWorthPaise").value(0));

        mockMvc.perform(get("/api/dashboard?month=September").header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ helpers

    private String dashboard(String month) throws Exception {
        return mockMvc.perform(get("/api/dashboard?month=" + month).header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private String signUp(String email) throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Test User", "email": "%s", "password": "password123"}
                        """.formatted(email)))
                .andExpect(status().isCreated());

        MvcResult login = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "password123"}
                        """.formatted(email)))
                .andExpect(status().isOk())
                .andReturn();

        String token = JsonPath.read(login.getResponse().getContentAsString(), "$.accessToken");
        return "Bearer " + token;
    }

    private long createAccount(String auth, String name, long openingBalancePaise) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/accounts")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "type": "BANK", "openingBalancePaise": %d}
                        """.formatted(name, openingBalancePaise)))
                .andExpect(status().isCreated())
                .andReturn();
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private long categoryId(String name) throws Exception {
        String json = mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        List<Number> ids = JsonPath.read(json, "$[?(@.name == '" + name + "')].id");
        return ids.get(0).longValue();
    }

    private void add(long accountId, Long categoryId, long amountPaise, String date, String description) throws Exception {
        mockMvc.perform(post("/api/transactions")
                .header(HttpHeaders.AUTHORIZATION, arjun)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"accountId": %d, "categoryId": %s, "amountPaise": %d, "date": "%s", "description": "%s"}
                        """.formatted(accountId, categoryId == null ? "null" : categoryId.toString(),
                        amountPaise, date, description)))
                .andExpect(status().isCreated());
    }
}
