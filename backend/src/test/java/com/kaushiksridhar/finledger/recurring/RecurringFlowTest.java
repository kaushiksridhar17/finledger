package com.kaushiksridhar.finledger.recurring;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.kaushiksridhar.finledger.common.AppTime;
import com.kaushiksridhar.finledger.support.ApiTestClient;

/**
 * Detecting repeating payments through the API, confirming and dismissing them, and bill reminders.
 * Dates are built backwards from today (Indian time) so the next payment always falls in the next few days.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RecurringFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ApiTestClient api;
    private String arjun;
    private long hdfc;
    private long subscriptions;
    private final LocalDate today = AppTime.today(Clock.systemUTC());

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM users");
        api = new ApiTestClient(mockMvc);
        arjun = api.signUp("arjun@example.com");
        hdfc = api.createAccount(arjun, "HDFC Savings");
        subscriptions = api.categoryId(arjun, "Subscriptions");
    }

    @Test
    @DisplayName("a monthly charge is suggested, and confirming it sends a reminder when it's due within 3 days")
    void suggestConfirmRemind() throws Exception {
        LocalDate last = addNetflixDueInTwoDays();

        MvcResult scan = mockMvc.perform(post("/api/recurring/scan").header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.found").value(1))
                .andExpect(jsonPath("$.newSuggestions").value(1))
                .andReturn();
        assertThat(scan.getResponse().getStatus()).isEqualTo(200);

        String overview = api.body(arjun, "/api/recurring");
        assertThat((String) ApiTestClient.read(overview, "$.items[0].name")).isEqualTo("Netflix");
        assertThat((String) ApiTestClient.read(overview, "$.items[0].status")).isEqualTo("SUGGESTED");
        assertThat((String) ApiTestClient.read(overview, "$.items[0].frequency")).isEqualTo("MONTHLY");
        assertThat((String) ApiTestClient.read(overview, "$.items[0].nextDueOn")).isEqualTo(last.plusMonths(1).toString());
        assertThat(ApiTestClient.number(overview, "$.items[0].amountPaise")).isEqualTo(64_900);
        assertThat(ApiTestClient.number(overview, "$.confirmedMonthlyOutPaise")).isZero();

        // Only suggested so far: a "found" notification, but no bill reminder yet
        assertThat(notificationTypes()).containsExactly("RECURRING_FOUND");

        long id = ApiTestClient.number(overview, "$.items[0].id");
        mockMvc.perform(post("/api/recurring/" + id + "/confirm").header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(notificationTypes()).contains("BILL_DUE");
        List<String> titles = ApiTestClient.read(api.body(arjun, "/api/notifications"), "$.items[*].title");
        assertThat(titles).anyMatch(title -> title.startsWith("Netflix is due"));

        assertThat(ApiTestClient.number(api.body(arjun, "/api/recurring"), "$.confirmedMonthlyOutPaise")).isEqualTo(64_900);
        List<String> upcoming = ApiTestClient.read(api.body(arjun, "/api/recurring/upcoming?days=14"), "$[*].name");
        assertThat(upcoming).containsExactly("Netflix");
    }

    @Test
    @DisplayName("a rescan never overrides the user's choice and doesn't suggest the same payment twice")
    void rescanKeepsChoice() throws Exception {
        addNetflixDueInTwoDays();
        mockMvc.perform(post("/api/recurring/scan").header(HttpHeaders.AUTHORIZATION, arjun)).andExpect(status().isOk());

        long id = ApiTestClient.number(api.body(arjun, "/api/recurring"), "$.items[0].id");
        mockMvc.perform(post("/api/recurring/" + id + "/dismiss").header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/recurring/scan").header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(jsonPath("$.found").value(1))
                .andExpect(jsonPath("$.newSuggestions").value(0));

        assertThat((String) ApiTestClient.read(api.body(arjun, "/api/recurring"), "$.items[0].status")).isEqualTo("DISMISSED");
        assertThat(notificationTypes()).doesNotContain("BILL_DUE");
    }

    @Test
    @DisplayName("irregular spending isn't suggested")
    void irregularNotSuggested() throws Exception {
        int[] daysAgo = { 2, 3, 7, 12, 13, 19, 25, 26, 40, 41, 55, 58, 70, 90 };
        for (int d : daysAgo) {
            api.addTransaction(arjun, hdfc, null, -43_200, today.minusDays(d), "Swiggy order", "Swiggy");
        }

        mockMvc.perform(post("/api/recurring/scan").header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(jsonPath("$.found").value(0));
    }

    @Test
    @DisplayName("nobody can confirm or dismiss someone else's recurring payment")
    void privacy() throws Exception {
        addNetflixDueInTwoDays();
        mockMvc.perform(post("/api/recurring/scan").header(HttpHeaders.AUTHORIZATION, arjun)).andExpect(status().isOk());
        long id = ApiTestClient.number(api.body(arjun, "/api/recurring"), "$.items[0].id");

        String priya = api.signUp("priya@example.com");
        mockMvc.perform(post("/api/recurring/" + id + "/confirm").header(HttpHeaders.AUTHORIZATION, priya))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/recurring/" + id + "/dismiss").header(HttpHeaders.AUTHORIZATION, priya))
                .andExpect(status().isNotFound());
        assertThat(ApiTestClient.number(api.body(priya, "/api/recurring"), "$.items.length()")).isZero();
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Four monthly Netflix charges whose next one is due within the next 3 days. Returns the last date.
     * Month ends make "one month before" fuzzy (31 March minus a month is 28 February), so this tries
     * a few days until last + 1 month really lands inside the reminder window.
     */
    private LocalDate addNetflixDueInTwoDays() throws Exception {
        LocalDate last = null;
        for (int offset = 2; offset <= 3 + 2 && last == null; offset++) {
            LocalDate candidate = today.plusDays(offset % 4).minusMonths(1);
            LocalDate next = candidate.plusMonths(1);
            if (!next.isBefore(today) && !next.isAfter(today.plusDays(3))) {
                last = candidate;
            }
        }
        for (int monthsBack = 3; monthsBack >= 0; monthsBack--) {
            api.addTransaction(arjun, hdfc, subscriptions, -64_900, last.minusMonths(monthsBack), "NETFLIX.COM", "Netflix");
        }
        return last;
    }

    private List<String> notificationTypes() throws Exception {
        return ApiTestClient.read(api.body(arjun, "/api/notifications"), "$.items[*].type");
    }
}
