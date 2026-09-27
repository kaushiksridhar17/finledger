package com.kaushiksridhar.finledger.split;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.kaushiksridhar.finledger.common.AppTime;
import com.kaushiksridhar.finledger.support.ApiTestClient;

/**
 * Spotting friends paying you back in your bank transactions. In every test, you paid Rs 3,600 for a
 * trip five days ago, split equally with Rohan and Neha, so each of them owes you Rs 1,200.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class PaymentMatchFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ApiTestClient api;
    private String arjun;
    private long hdfc;
    private long groupId;
    private long me;
    private long rohan;
    private long neha;
    private final LocalDate today = AppTime.today(Clock.systemUTC());

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM users");
        api = new ApiTestClient(mockMvc);
        arjun = api.signUp("arjun@example.com");
        hdfc = api.createAccount(arjun, "HDFC Savings");

        String group = send(post("/api/groups"), """
                {"name": "Goa trip", "friends": [
                    {"name": "Rohan Kulkarni", "upiId": "rohan.k@okaxis"},
                    {"name": "Neha Sharma"}]}""");
        groupId = ApiTestClient.number(group, "$.id");
        me = memberId(group, "Test User");
        rohan = memberId(group, "Rohan Kulkarni");
        neha = memberId(group, "Neha Sharma");

        send(post("/api/groups/" + groupId + "/expenses"), """
                {"description": "Villa", "amountPaise": 360000, "date": "%s", "paidByMemberId": %d, "splitType": "EQUAL",
                 "shares": [{"memberId": %d}, {"memberId": %d}, {"memberId": %d}]}
                """.formatted(today.minusDays(5), me, me, rohan, neha));
    }

    @Test
    @DisplayName("a credit naming the friend, for what they owe, is suggested; accepting it records their payment")
    void suggestAndAccept() throws Exception {
        api.addTransaction(arjun, hdfc, null, 120_000, today.minusDays(1), "UPI/CR/412345678901/ROHAN KULKARNI/okaxis/Goa", null);

        String group = detail();
        assertThat(ApiTestClient.number(group, "$.suggestions.length()")).isEqualTo(1);
        assertThat((String) ApiTestClient.read(group, "$.suggestions[0].memberName")).isEqualTo("Rohan Kulkarni");
        assertThat(ApiTestClient.number(group, "$.suggestions[0].amountPaise")).isEqualTo(120_000);

        String notifications = api.body(arjun, "/api/notifications");
        List<String> titles = ApiTestClient.read(notifications, "$.items[?(@.type == 'SETTLEMENT_MATCH')].title");
        assertThat(titles).containsExactly("Rohan Kulkarni may have paid you back");
        List<String> links = ApiTestClient.read(notifications, "$.items[?(@.type == 'SETTLEMENT_MATCH')].link");
        assertThat(links).containsExactly("/split/" + groupId);

        long matchId = ApiTestClient.number(group, "$.suggestions[0].id");
        String accepted = send(post("/api/groups/" + groupId + "/matches/" + matchId + "/accept"), null);

        assertThat(ApiTestClient.number(accepted, "$.suggestions.length()")).isZero();
        assertThat((String) ApiTestClient.read(accepted, "$.settlements[0].method")).isEqualTo("MATCHED");
        assertThat(ApiTestClient.number(accepted, "$.settlements[0].fromMemberId")).isEqualTo(rohan);
        assertThat(ApiTestClient.number(accepted, "$.settlements[0].toMemberId")).isEqualTo(me);
        assertThat(balanceOf(accepted, rohan)).isZero();
        assertThat(ApiTestClient.<List<String>>read(accepted, "$.settleUp[*].fromName")).containsExactly("Neha Sharma");

        // The bank transaction is still in the ledger, now linked to the repayment
        Long linked = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM settlements WHERE transaction_id IS NOT NULL", Long.class);
        assertThat(linked).isEqualTo(1L);
    }

    @Test
    @DisplayName("the wrong amount, a stranger, money from before the trip or money going out is never suggested")
    void nonMatches() throws Exception {
        api.addTransaction(arjun, hdfc, null, 100_000, today.minusDays(1), "UPI/CR/ROHAN KULKARNI/okaxis", null);
        api.addTransaction(arjun, hdfc, null, 120_000, today.minusDays(1), "UPI/CR/KABIR SINGH/okicici", null);
        api.addTransaction(arjun, hdfc, null, 120_000, today.minusDays(10), "UPI/CR/ROHAN KULKARNI/okaxis", null);
        api.addTransaction(arjun, hdfc, null, -120_000, today.minusDays(1), "UPI/DR/ROHAN KULKARNI/okaxis", null);

        assertThat(ApiTestClient.number(detail(), "$.suggestions.length()")).isZero();
    }

    @Test
    @DisplayName("a UPI ID is enough to match, and a dismissed suggestion never comes back")
    void upiIdAndDismiss() throws Exception {
        api.addTransaction(arjun, hdfc, null, 120_000, today.minusDays(1), "UPI/CR/412345/nk.sharma@oksbi/trip", null);
        assertThat(ApiTestClient.number(detail(), "$.suggestions.length()")).isZero();

        // Adding Neha's UPI ID lets the same credit be recognised as hers
        String withUpi = send(put("/api/groups/" + groupId + "/members/" + neha), """
                {"name": "Neha Sharma", "upiId": "nk.sharma@oksbi"}""");
        assertThat(ApiTestClient.number(withUpi, "$.suggestions.length()")).isEqualTo(1);
        assertThat((String) ApiTestClient.read(withUpi, "$.suggestions[0].memberName")).isEqualTo("Neha Sharma");

        long matchId = ApiTestClient.number(withUpi, "$.suggestions[0].id");
        String dismissed = send(post("/api/groups/" + groupId + "/matches/" + matchId + "/dismiss"), null);
        assertThat(ApiTestClient.number(dismissed, "$.suggestions.length()")).isZero();

        // Any later change triggers a rescan, which must respect the "no"
        api.addTransaction(arjun, hdfc, null, -5_000, today, "Chai", null);
        assertThat(ApiTestClient.number(detail(), "$.suggestions.length()")).isZero();
        assertThat(balanceOf(detail(), neha)).isEqualTo(-120_000);
    }

    @Test
    @DisplayName("if the friend settles up some other way, the suggestion is withdrawn")
    void withdrawnWhenSettled() throws Exception {
        api.addTransaction(arjun, hdfc, null, 120_000, today.minusDays(1), "UPI/CR/ROHAN KULKARNI/okaxis", null);
        assertThat(ApiTestClient.number(detail(), "$.suggestions.length()")).isEqualTo(1);

        String settled = send(post("/api/groups/" + groupId + "/settlements"), """
                {"fromMemberId": %d, "toMemberId": %d, "amountPaise": 120000, "date": "%s", "note": "Cash"}
                """.formatted(rohan, me, today));

        assertThat(ApiTestClient.number(settled, "$.suggestions.length()")).isZero();
        assertThat(balanceOf(settled, rohan)).isZero();
    }

    // ------------------------------------------------------------------ helpers

    private String detail() throws Exception {
        return api.body(arjun, "/api/groups/" + groupId);
    }

    /** Sends the request as the test user and expects a 2xx response. */
    private String send(MockHttpServletRequestBuilder request, String body) throws Exception {
        request.header(HttpHeaders.AUTHORIZATION, arjun);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(body);
        }
        return mockMvc.perform(request)
                .andExpect(status().is2xxSuccessful())
                .andReturn().getResponse().getContentAsString();
    }

    private static long memberId(String group, String name) {
        List<Number> ids = ApiTestClient.read(group, "$.members[?(@.name == '" + name + "')].id");
        return ids.get(0).longValue();
    }

    private static long balanceOf(String group, long memberId) {
        List<Number> balances = ApiTestClient.read(group, "$.members[?(@.id == " + memberId + ")].balancePaise");
        return balances.get(0).longValue();
    }
}
