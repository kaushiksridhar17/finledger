package com.kaushiksridhar.finledger.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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

/**
 * Budgets and their alerts through the real API. Transactions are dated today (Indian time),
 * because alerts only care about the current month.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class BudgetFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ApiTestClient api;
    private String arjun;
    private long hdfc;
    private long food;
    private final LocalDate today = AppTime.today(Clock.systemUTC());

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM users");
        api = new ApiTestClient(mockMvc);
        arjun = api.signUp("arjun@example.com");
        hdfc = api.createAccount(arjun, "HDFC Savings");
        food = api.categoryId(arjun, "Food & Dining");
    }

    @Test
    @DisplayName("a budget shows how much of it this month's spending has used")
    void progress() throws Exception {
        createBudget(food, 100_000).andExpect(status().isCreated())
                .andExpect(jsonPath("$.spentPaise").value(0))
                .andExpect(jsonPath("$.status").value("ON_TRACK"));

        spend(50_000);

        String budgets = api.body(arjun, "/api/budgets");
        assertThat(ApiTestClient.number(budgets, "$[0].spentPaise")).isEqualTo(50_000);
        assertThat(ApiTestClient.number(budgets, "$[0].remainingPaise")).isEqualTo(50_000);
        assertThat(ApiTestClient.number(budgets, "$[0].percent")).isEqualTo(50);
        assertThat((String) ApiTestClient.read(budgets, "$[0].status")).isEqualTo("ON_TRACK");
    }

    @Test
    @DisplayName("crossing 80% and then 100% sends one warning and one over-budget alert, never repeated")
    void alertsOncePerLevel() throws Exception {
        createBudget(food, 100_000).andExpect(status().isCreated());

        spend(85_000);
        spend(1_000);
        assertThat(notificationTypes()).containsExactly("BUDGET_WARNING");

        spend(20_000);
        spend(5_000);
        assertThat(notificationTypes()).containsExactly("BUDGET_EXCEEDED", "BUDGET_WARNING");

        String budgets = api.body(arjun, "/api/budgets");
        assertThat((String) ApiTestClient.read(budgets, "$[0].status")).isEqualTo("OVER");
        assertThat(ApiTestClient.number(budgets, "$[0].remainingPaise")).isEqualTo(100_000 - 111_000);
    }

    @Test
    @DisplayName("a refund in the same category brings spending back down")
    void refundsReduceSpending() throws Exception {
        createBudget(food, 100_000).andExpect(status().isCreated());
        spend(80_000);
        api.addTransaction(arjun, hdfc, food, 30_000, today, "Swiggy refund", "Swiggy");

        assertThat(ApiTestClient.number(api.body(arjun, "/api/budgets"), "$[0].spentPaise")).isEqualTo(50_000);
    }

    @Test
    @DisplayName("only spending categories can have a budget, and only one each")
    void validation() throws Exception {
        createBudget(api.categoryId(arjun, "Salary"), 100_000).andExpect(status().isBadRequest());
        createBudget(food, 100_000).andExpect(status().isCreated());
        createBudget(food, 200_000).andExpect(status().isConflict());
        createBudget(food, 0).andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("lowering the limit below what's been spent triggers the alert straight away; deleting works")
    void updateAndDelete() throws Exception {
        long budgetId = ApiTestClient.idOf(createBudget(food, 100_000).andReturn());
        spend(60_000);
        assertThat(notificationTypes()).isEmpty();

        mockMvc.perform(put("/api/budgets/" + budgetId)
                .header(HttpHeaders.AUTHORIZATION, arjun)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"limitPaise\": 50000}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("OVER"));
        assertThat(notificationTypes()).containsExactly("BUDGET_EXCEEDED");

        mockMvc.perform(delete("/api/budgets/" + budgetId).header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/budgets").header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("nobody can see or change someone else's budget")
    void privacy() throws Exception {
        long budgetId = ApiTestClient.idOf(createBudget(food, 100_000).andReturn());
        String priya = api.signUp("priya@example.com");

        mockMvc.perform(get("/api/budgets").header(HttpHeaders.AUTHORIZATION, priya))
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(put("/api/budgets/" + budgetId)
                .header(HttpHeaders.AUTHORIZATION, priya)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"limitPaise\": 1}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/budgets/" + budgetId).header(HttpHeaders.AUTHORIZATION, priya))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("suggestions average the last three complete months and ignore this month")
    void suggestions() throws Exception {
        for (int monthsAgo = 1; monthsAgo <= 3; monthsAgo++) {
            api.addTransaction(arjun, hdfc, food, -300_000, today.minusMonths(monthsAgo).withDayOfMonth(10),
                    "Dinner", null);
        }
        spend(999_000);   // this month: not part of the average

        String suggestions = api.body(arjun, "/api/budgets/suggestions");
        List<String> names = ApiTestClient.read(suggestions, "$[*].name");
        assertThat(names).containsExactly("Food & Dining");
        assertThat(ApiTestClient.number(suggestions, "$[0].averageMonthlyPaise")).isEqualTo(300_000);
    }

    @Test
    @DisplayName("notifications can be marked read one at a time or all together, only by their owner")
    void readingNotifications() throws Exception {
        createBudget(food, 100_000).andExpect(status().isCreated());
        spend(90_000);
        spend(20_000);

        String list = api.body(arjun, "/api/notifications");
        assertThat(ApiTestClient.number(list, "$.unreadCount")).isEqualTo(2);
        long firstId = ApiTestClient.number(list, "$.items[0].id");

        String priya = api.signUp("priya@example.com");
        mockMvc.perform(post("/api/notifications/" + firstId + "/read").header(HttpHeaders.AUTHORIZATION, priya))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/notifications/" + firstId + "/read").header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isNoContent());
        assertThat(ApiTestClient.number(api.body(arjun, "/api/notifications"), "$.unreadCount")).isEqualTo(1);

        mockMvc.perform(post("/api/notifications/read-all").header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isNoContent());
        assertThat(ApiTestClient.number(api.body(arjun, "/api/notifications"), "$.unreadCount")).isZero();
    }

    // ------------------------------------------------------------------ helpers

    private ResultActions createBudget(long categoryId, long limitPaise) throws Exception {
        return mockMvc.perform(post("/api/budgets")
                .header(HttpHeaders.AUTHORIZATION, arjun)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"categoryId": %d, "limitPaise": %d}
                        """.formatted(categoryId, limitPaise)));
    }

    private void spend(long paise) throws Exception {
        api.addTransaction(arjun, hdfc, food, -paise, today, "Food", null);
    }

    private List<String> notificationTypes() throws Exception {
        return ApiTestClient.read(api.body(arjun, "/api/notifications"), "$.items[*].type");
    }
}
