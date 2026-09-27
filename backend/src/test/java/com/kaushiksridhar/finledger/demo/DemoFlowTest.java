package com.kaushiksridhar.finledger.demo;

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
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@TestPropertySource(properties = "finledger.demo.max-active=3")
class DemoFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private DemoCleanupJob cleanupJob;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    @DisplayName("the demo button logs in as a new demo user with four accounts and a year of transactions")
    void demoLoginCreatesAYearOfData() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/auth/demo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.demo").value(true))
                .andExpect(jsonPath("$.user.name").value(DemoService.DEMO_NAME))
                .andReturn();

        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("finledger_refresh=");
        String auth = bearer(result);

        List<String> accountNames = JsonPath.read(body("/api/accounts", auth), "$[*].name");
        assertThat(accountNames).containsExactlyInAnyOrder("HDFC Savings", "ICICI Credit Card", "Cash", "Paytm Wallet");

        Number totalTransactions = JsonPath.read(body("/api/transactions?size=1", auth), "$.totalItems");
        assertThat(totalTransactions.intValue()).isGreaterThan(400);

        String dashboard = body("/api/dashboard", auth);
        List<Object> months = JsonPath.read(dashboard, "$.monthly");
        List<String> spendingCategories = JsonPath.read(dashboard, "$.spendingByCategory[*].name");
        Number netWorth = JsonPath.read(dashboard, "$.netWorthPaise");

        assertThat(months).hasSize(12);
        assertThat(spendingCategories).isNotEmpty().doesNotContain("Transfer", "Investment", "Credit Card Payment");
        assertThat(netWorth.longValue()).isPositive();
    }

    @Test
    @DisplayName("two visitors get two separate demo users")
    void eachDemoIsSeparate() throws Exception {
        String first = bearer(mockMvc.perform(post("/api/auth/demo")).andExpect(status().isOk()).andReturn());
        String second = bearer(mockMvc.perform(post("/api/auth/demo")).andExpect(status().isOk()).andReturn());

        Number firstUser = JsonPath.read(body("/api/users/me", first), "$.id");
        Number secondUser = JsonPath.read(body("/api/users/me", second), "$.id");
        assertThat(firstUser).isNotEqualTo(secondUser);

        Number oneOfFirstsTransactions = JsonPath.read(body("/api/transactions?size=1", first), "$.items[0].id");
        mockMvc.perform(get("/api/transactions/" + oneOfFirstsTransactions).header(HttpHeaders.AUTHORIZATION, second))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("once the live demo limit is reached, new demos are refused with 503")
    void maxActiveDemos() throws Exception {
        for (int i = 0; i < 3; i++) {
            mockMvc.perform(post("/api/auth/demo")).andExpect(status().isOk());
        }
        mockMvc.perform(post("/api/auth/demo")).andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("the cleanup job deletes expired demo users and all their data, and never touches real users")
    void cleanupDeletesOnlyExpiredDemoUsers() throws Exception {
        mockMvc.perform(post("/api/auth/demo")).andExpect(status().isOk());
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Real Person", "email": "real@example.com", "password": "password123"}
                        """))
                .andExpect(status().isCreated());

        // Pretend a day has passed for the demo user
        jdbcTemplate.update("UPDATE users SET demo_expires_at = '2000-01-01 00:00:00' WHERE demo_expires_at IS NOT NULL");

        cleanupJob.deleteExpiredDemoUsers();

        assertThat(count("SELECT COUNT(*) FROM users WHERE email LIKE 'demo-%'")).isZero();
        assertThat(count("SELECT COUNT(*) FROM transactions")).isZero();
        assertThat(count("SELECT COUNT(*) FROM accounts")).isZero();
        assertThat(count("SELECT COUNT(*) FROM users WHERE email = 'real@example.com'")).isEqualTo(1);
    }

    // ------------------------------------------------------------------ helpers

    private static String bearer(MvcResult result) throws Exception {
        String token = JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
        return "Bearer " + token;
    }

    private String body(String url, String auth) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
