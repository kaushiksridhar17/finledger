package com.kaushiksridhar.finledger.split;

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
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.kaushiksridhar.finledger.common.AppTime;
import com.kaushiksridhar.finledger.support.ApiTestClient;

/** Split groups through the real API: members, the four kinds of split, balances, settling up and privacy. */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class GroupFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private ApiTestClient api;
    private String arjun;
    private final LocalDate today = AppTime.today(Clock.systemUTC());

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM users");
        api = new ApiTestClient(mockMvc);
        arjun = api.signUp("arjun@example.com");    // named "Test User"
    }

    @Test
    @DisplayName("a new group has you and your friends, and an equal split sets everyone's balance")
    void createAndSplitEqually() throws Exception {
        String group = createGroup("Goa trip", """
                [{"name": "Rohan", "upiId": "Rohan.K@OKAXIS"}, {"name": "Neha", "upiId": ""}]""");
        long groupId = ApiTestClient.number(group, "$.id");
        long me = selfId(group);
        long rohan = memberId(group, "Rohan");
        long neha = memberId(group, "Neha");

        assertThat(ApiTestClient.<List<String>>read(group, "$.members[*].name")).containsExactly("Test User", "Rohan", "Neha");
        assertThat(ApiTestClient.<List<String>>read(group, "$.members[?(@.name == 'Rohan')].upiId")).containsExactly("rohan.k@okaxis");
        assertThat(ApiTestClient.<List<Object>>read(group, "$.members[?(@.name == 'Neha')].upiId")).containsExactly((Object) null);

        String after = addExpense(groupId, expense("Flights", 300_000, me, "EQUAL", shares(me, null, rohan, null, neha, null)),
                status().isCreated());

        assertThat(balanceOf(after, me)).isEqualTo(200_000);
        assertThat(balanceOf(after, rohan)).isEqualTo(-100_000);
        assertThat(balanceOf(after, neha)).isEqualTo(-100_000);
        assertThat(ApiTestClient.number(after, "$.totalSpentPaise")).isEqualTo(300_000);
        assertThat(ApiTestClient.number(after, "$.mySharePaise")).isEqualTo(100_000);
        assertThat(ApiTestClient.number(after, "$.myBalancePaise")).isEqualTo(200_000);

        // Both friends pay you directly
        assertThat(ApiTestClient.<List<String>>read(after, "$.settleUp[*].fromName")).containsExactly("Rohan", "Neha");
        assertThat(ApiTestClient.<List<String>>read(after, "$.settleUp[*].toName")).containsExactly("Test User", "Test User");

        String list = api.body(arjun, "/api/groups");
        assertThat((String) ApiTestClient.read(list, "$[0].name")).isEqualTo("Goa trip");
        assertThat(ApiTestClient.<List<String>>read(list, "$[0].friendNames")).containsExactly("Rohan", "Neha");
        assertThat(ApiTestClient.number(list, "$[0].myBalancePaise")).isEqualTo(200_000);
        assertThat(ApiTestClient.number(list, "$[0].totalSpentPaise")).isEqualTo(300_000);
    }

    @Test
    @DisplayName("Rs 100 split three ways is 33.34 + 33.33 + 33.33, never a paisa more or less")
    void unevenSplitAddsUp() throws Exception {
        String group = createGroup("Flat", """
                [{"name": "Rohan"}, {"name": "Neha"}]""");
        long groupId = ApiTestClient.number(group, "$.id");
        long me = selfId(group);

        String after = addExpense(groupId, expense("Milk", 10_000, me, "EQUAL",
                shares(memberId(group, "Neha"), null, me, null, memberId(group, "Rohan"), null)), status().isCreated());

        // Shares are kept in member order, whatever order they were sent in, so you get the spare paisa
        List<Number> amounts = ApiTestClient.read(after, "$.expenses[0].shares[*].sharePaise");
        assertThat(amounts.stream().map(Number::longValue).toList()).containsExactly(3_334L, 3_333L, 3_333L);
        assertThat(ApiTestClient.<List<String>>read(after, "$.expenses[0].shares[*].memberName"))
                .containsExactly("Test User", "Rohan", "Neha");
    }

    @Test
    @DisplayName("splits that don't add up, or include strangers, are refused with a readable message")
    void invalidSplits() throws Exception {
        String group = createGroup("Trip", """
                [{"name": "Rohan"}]""");
        long groupId = ApiTestClient.number(group, "$.id");
        long me = selfId(group);
        long rohan = memberId(group, "Rohan");

        mockMvc.perform(json(post("/api/groups/" + groupId + "/expenses"),
                expense("Hotel", 100_000, me, "PERCENT", shares(me, 5_000L, rohan, 4_000L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The percentages add up to 90% instead of 100%"));

        mockMvc.perform(json(post("/api/groups/" + groupId + "/expenses"),
                expense("Hotel", 100_000, me, "EXACT", shares(me, 60_000L, rohan, 30_000L))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("The amounts add up to \u20B9900, but the expense is \u20B91,000"));

        mockMvc.perform(json(post("/api/groups/" + groupId + "/expenses"),
                expense("Hotel", 100_000, me, "EQUAL", shares(me, null, 999_999L, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Everyone in the split must be in this group"));

        mockMvc.perform(json(post("/api/groups/" + groupId + "/expenses"),
                expense("Hotel", 100_000, 999_999L, "EQUAL", shares(me, null, rohan, null))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Choose who paid from the people in this group"));

        mockMvc.perform(json(post("/api/groups/" + groupId + "/expenses"), """
                {"description": "", "amountPaise": -5, "date": "%s", "paidByMemberId": %d, "splitType": "EQUAL", "shares": []}
                """.formatted(today, me)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.description").exists())
                .andExpect(jsonPath("$.errors.amountPaise").exists())
                .andExpect(jsonPath("$.errors.shares").exists());

        assertThat(ApiTestClient.number(api.body(arjun, "/api/groups/" + groupId), "$.expenses.length()")).isZero();
    }

    @Test
    @DisplayName("a chain of debts is simplified, and recording the payment settles the group")
    void simplifyAndSettle() throws Exception {
        String group = createGroup("Weekend", """
                [{"name": "Rohan"}, {"name": "Neha"}]""");
        long groupId = ApiTestClient.number(group, "$.id");
        long me = selfId(group);
        long rohan = memberId(group, "Rohan");
        long neha = memberId(group, "Neha");

        // Neha owes Rohan Rs 300, and Rohan owes you Rs 300
        addExpense(groupId, expense("Tickets", 60_000, rohan, "EXACT", shares(rohan, 30_000L, neha, 30_000L)), status().isCreated());
        String after = addExpense(groupId, expense("Cab", 60_000, me, "EXACT", shares(me, 30_000L, rohan, 30_000L)),
                status().isCreated());

        // Rohan is square, so one payment settles everything: Neha pays you
        assertThat(balanceOf(after, rohan)).isZero();
        assertThat(ApiTestClient.number(after, "$.settleUp.length()")).isEqualTo(1);
        assertThat(ApiTestClient.number(after, "$.settleUp[0].fromMemberId")).isEqualTo(neha);
        assertThat(ApiTestClient.number(after, "$.settleUp[0].toMemberId")).isEqualTo(me);
        assertThat(ApiTestClient.number(after, "$.settleUp[0].amountPaise")).isEqualTo(30_000);

        String settled = send(post("/api/groups/" + groupId + "/settlements"), """
                {"fromMemberId": %d, "toMemberId": %d, "amountPaise": 30000, "date": "%s", "note": "GPay"}
                """.formatted(neha, me, today), status().isCreated());

        assertThat(ApiTestClient.number(settled, "$.settleUp.length()")).isZero();
        assertThat(ApiTestClient.<List<Number>>read(settled, "$.members[*].balancePaise"))
                .allMatch(balance -> balance.longValue() == 0);
        assertThat((String) ApiTestClient.read(settled, "$.settlements[0].method")).isEqualTo("MANUAL");
        assertThat((String) ApiTestClient.read(settled, "$.settlements[0].note")).isEqualTo("GPay");

        mockMvc.perform(json(post("/api/groups/" + groupId + "/settlements"), """
                {"fromMemberId": %d, "toMemberId": %d, "amountPaise": 100, "date": "%s"}
                """.formatted(me, me, today)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Choose two different people"));

        // Undoing the payment brings the debt back
        long settlementId = ApiTestClient.number(settled, "$.settlements[0].id");
        String undone = send(delete("/api/groups/" + groupId + "/settlements/" + settlementId), null, status().isOk());
        assertThat(balanceOf(undone, neha)).isEqualTo(-30_000);
    }

    @Test
    @DisplayName("editing an expense replaces its shares, and deleting it clears the balances")
    void editAndDeleteExpense() throws Exception {
        String group = createGroup("Stay", """
                [{"name": "Rohan"}, {"name": "Neha"}]""");
        long groupId = ApiTestClient.number(group, "$.id");
        long me = selfId(group);
        long rohan = memberId(group, "Rohan");
        long neha = memberId(group, "Neha");

        String created = addExpense(groupId, expense("Homestay", 90_000, me, "EQUAL",
                shares(me, null, rohan, null, neha, null)), status().isCreated());
        long expenseId = ApiTestClient.number(created, "$.expenses[0].id");

        // Neha didn't stay after all, and Rohan stayed two nights to your one
        String edited = send(put("/api/groups/" + groupId + "/expenses/" + expenseId),
                expense("Homestay, 3 nights", 90_000, me, "SHARES", shares(me, 1L, rohan, 2L)), status().isOk());

        assertThat((String) ApiTestClient.read(edited, "$.expenses[0].description")).isEqualTo("Homestay, 3 nights");
        assertThat(ApiTestClient.<List<String>>read(edited, "$.expenses[0].shares[*].memberName")).containsExactly("Test User", "Rohan");
        List<Number> values = ApiTestClient.read(edited, "$.expenses[0].shares[*].value");
        assertThat(values.stream().map(Number::longValue).toList()).containsExactly(1L, 2L);
        assertThat(balanceOf(edited, me)).isEqualTo(60_000);
        assertThat(balanceOf(edited, rohan)).isEqualTo(-60_000);
        assertThat(balanceOf(edited, neha)).isZero();

        String deleted = send(delete("/api/groups/" + groupId + "/expenses/" + expenseId), null, status().isOk());
        assertThat(ApiTestClient.number(deleted, "$.expenses.length()")).isZero();
        assertThat(balanceOf(deleted, me)).isZero();
    }

    @Test
    @DisplayName("names must be unique, UPI IDs are checked, and people with history can't be removed")
    void members() throws Exception {
        String group = createGroup("Office lunch", """
                [{"name": "Rohan"}]""");
        long groupId = ApiTestClient.number(group, "$.id");
        long me = selfId(group);
        long rohan = memberId(group, "Rohan");

        mockMvc.perform(json(post("/api/groups/" + groupId + "/members"), """
                {"name": "rohan"}"""))
                .andExpect(status().isConflict());
        mockMvc.perform(json(post("/api/groups/" + groupId + "/members"), """
                {"name": "Kabir", "upiId": "not a upi id"}"""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.upiId").exists());

        String withKabir = send(post("/api/groups/" + groupId + "/members"), """
                {"name": "Kabir", "upiId": "kabir@okicici"}""", status().isCreated());
        long kabir = memberId(withKabir, "Kabir");
        assertThat(ApiTestClient.<List<Boolean>>read(withKabir, "$.members[?(@.name == 'Kabir')].removable")).containsExactly(true);

        // You can add your own UPI ID so friends can pay you
        String mine = send(put("/api/groups/" + groupId + "/members/" + me), """
                {"name": "Test User", "upiId": "me@okhdfcbank"}""", status().isOk());
        assertThat(ApiTestClient.<List<String>>read(mine, "$.members[?(@.self == true)].upiId")).containsExactly("me@okhdfcbank");

        addExpense(groupId, expense("Thali", 40_000, me, "EQUAL", shares(me, null, rohan, null)), status().isCreated());

        mockMvc.perform(delete("/api/groups/" + groupId + "/members/" + rohan).header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Rohan is part of some expenses or payments, so they can't be removed"));
        mockMvc.perform(delete("/api/groups/" + groupId + "/members/" + me).header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isBadRequest());

        String without = send(delete("/api/groups/" + groupId + "/members/" + kabir), null, status().isOk());
        assertThat(ApiTestClient.<List<String>>read(without, "$.members[*].name")).containsExactly("Test User", "Rohan");
    }

    @Test
    @DisplayName("nobody can see or change someone else's group")
    void privacy() throws Exception {
        String group = createGroup("Private trip", """
                [{"name": "Rohan"}]""");
        long groupId = ApiTestClient.number(group, "$.id");
        long me = selfId(group);

        String priya = api.signUp("priya@example.com");
        mockMvc.perform(get("/api/groups/" + groupId).header(HttpHeaders.AUTHORIZATION, priya))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/groups/" + groupId + "/expenses")
                .header(HttpHeaders.AUTHORIZATION, priya)
                .contentType(MediaType.APPLICATION_JSON)
                .content(expense("Sneaky", 1_000, me, "EQUAL", shares(me, null))))
                .andExpect(status().isNotFound());
        mockMvc.perform(delete("/api/groups/" + groupId).header(HttpHeaders.AUTHORIZATION, priya))
                .andExpect(status().isNotFound());
        assertThat(ApiTestClient.number(api.body(priya, "/api/groups"), "$.length()")).isZero();

        mockMvc.perform(get("/api/groups/" + groupId)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("deleting a group removes its members, expenses and payments")
    void deleteGroup() throws Exception {
        String group = createGroup("Old trip", """
                [{"name": "Rohan"}]""");
        long groupId = ApiTestClient.number(group, "$.id");
        long me = selfId(group);
        long rohan = memberId(group, "Rohan");
        addExpense(groupId, expense("Fuel", 20_000, me, "EQUAL", shares(me, null, rohan, null)), status().isCreated());
        send(post("/api/groups/" + groupId + "/settlements"), """
                {"fromMemberId": %d, "toMemberId": %d, "amountPaise": 10000, "date": "%s"}
                """.formatted(rohan, me, today), status().isCreated());

        mockMvc.perform(delete("/api/groups/" + groupId).header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/groups/" + groupId).header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isNotFound());
        assertThat(count("SELECT COUNT(*) FROM group_members")).isZero();
        assertThat(count("SELECT COUNT(*) FROM group_expenses")).isZero();
        assertThat(count("SELECT COUNT(*) FROM expense_shares")).isZero();
        assertThat(count("SELECT COUNT(*) FROM settlements")).isZero();
    }

    // ------------------------------------------------------------------ helpers

    private String createGroup(String name, String friendsJson) throws Exception {
        return send(post("/api/groups"), """
                {"name": "%s", "friends": %s}""".formatted(name, friendsJson), status().isCreated());
    }

    private String addExpense(long groupId, String body, ResultMatcher expected) throws Exception {
        return send(post("/api/groups/" + groupId + "/expenses"), body, expected);
    }

    private String expense(String description, long amountPaise, long paidBy, String splitType, String sharesJson) {
        return """
                {"description": "%s", "amountPaise": %d, "date": "%s", "paidByMemberId": %d,
                 "splitType": "%s", "shares": %s}
                """.formatted(description, amountPaise, today, paidBy, splitType, sharesJson);
    }

    /** shares(id1, value1, id2, value2, ...) as JSON; a null value is sent as null. */
    private static String shares(Object... idsAndValues) {
        StringBuilder json = new StringBuilder("[");
        for (int i = 0; i < idsAndValues.length; i += 2) {
            if (i > 0) {
                json.append(", ");
            }
            json.append("{\"memberId\": ").append(idsAndValues[i])
                    .append(", \"value\": ").append(idsAndValues[i + 1]).append('}');
        }
        return json.append(']').toString();
    }

    private MockHttpServletRequestBuilder json(MockHttpServletRequestBuilder request, String body) {
        return request.header(HttpHeaders.AUTHORIZATION, arjun)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body);
    }

    private String send(MockHttpServletRequestBuilder request, String body, ResultMatcher expected) throws Exception {
        MockHttpServletRequestBuilder withAuth = body == null
                ? request.header(HttpHeaders.AUTHORIZATION, arjun)
                : json(request, body);
        return mockMvc.perform(withAuth)
                .andExpect(expected)
                .andReturn().getResponse().getContentAsString();
    }

    private static long selfId(String group) {
        List<Number> ids = ApiTestClient.read(group, "$.members[?(@.self == true)].id");
        return ids.get(0).longValue();
    }

    private static long memberId(String group, String name) {
        List<Number> ids = ApiTestClient.read(group, "$.members[?(@.name == '" + name + "')].id");
        return ids.get(0).longValue();
    }

    private static long balanceOf(String group, long memberId) {
        List<Number> balances = ApiTestClient.read(group, "$.members[?(@.id == " + memberId + ")].balancePaise");
        return balances.get(0).longValue();
    }

    private long count(String sql) {
        Long value = jdbcTemplate.queryForObject(sql, Long.class);
        return value == null ? 0 : value;
    }
}
