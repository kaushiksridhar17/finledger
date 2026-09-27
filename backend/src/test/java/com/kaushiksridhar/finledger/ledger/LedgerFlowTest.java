package com.kaushiksridhar.finledger.ledger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;

/**
 * Accounts, categories and transactions through the real HTTP layer and the finledger_test database.
 * Two users (Arjun and Priya) are used to prove neither can see or touch the other's data.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class LedgerFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String arjun;
    private String priya;

    @BeforeEach
    void setUp() throws Exception {
        // Deleting users cascades to their sessions, accounts, categories and transactions.
        // Built-in categories (user_id NULL) are untouched.
        jdbcTemplate.update("DELETE FROM users");

        arjun = signUp("arjun@example.com");
        priya = signUp("priya@example.com");
    }

    // ------------------------------------------------------------------ categories

    @Test
    @DisplayName("built-in categories are visible to every user")
    void builtInCategoriesVisible() throws Exception {
        mockMvc.perform(get("/api/categories").header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == 'Food & Dining')].kind").value("EXPENSE"))
                .andExpect(jsonPath("$[?(@.name == 'Salary')].kind").value("INCOME"))
                .andExpect(jsonPath("$[?(@.name == 'Salary')].system").value(true));
    }

    @Test
    @DisplayName("a custom category is visible and usable only by the user who made it")
    void customCategoryIsPrivate() throws Exception {
        MvcResult created = mockMvc.perform(post("/api/categories")
                .header(HttpHeaders.AUTHORIZATION, arjun)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Pet Care", "kind": "EXPENSE", "color": "#AABBCC"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.system").value(false))
                .andExpect(jsonPath("$.color").value("#aabbcc"))
                .andReturn();
        long petCare = idOf(created);

        List<String> priyasNames = JsonPath.read(body(get("/api/categories"), priya), "$[*].name");
        assertThat(priyasNames).doesNotContain("Pet Care");

        long priyasAccount = createAccount(priya, "Priya Bank", "BANK", 0);
        mockMvc.perform(post("/api/transactions")
                .header(HttpHeaders.AUTHORIZATION, priya)
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson(priyasAccount, petCare, -10000, "2026-09-01", "Vet")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("a category name that already exists (built-in or yours) is rejected")
    void duplicateCategoryName() throws Exception {
        mockMvc.perform(post("/api/categories")
                .header(HttpHeaders.AUTHORIZATION, arjun)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "groceries", "kind": "EXPENSE", "color": "#123456"}
                        """))
                .andExpect(status().isConflict());
    }

    // ------------------------------------------------------------------ accounts

    @Test
    @DisplayName("an account's balance is its opening balance plus all its transactions")
    void balanceIsOpeningPlusTransactions() throws Exception {
        long hdfc = createAccount(arjun, "HDFC Savings", "BANK", 1_000_000);

        createTransaction(arjun, hdfc, categoryId(arjun, "Food & Dining"), -45_000, "2026-09-02", "Swiggy order");
        createTransaction(arjun, hdfc, categoryId(arjun, "Salary"), 5_000_000, "2026-09-01", "September salary");

        assertThat(balanceOf(arjun, hdfc)).isEqualTo(1_000_000 - 45_000 + 5_000_000);
    }

    @Test
    @DisplayName("two accounts can't share a name, ignoring capitals")
    void duplicateAccountName() throws Exception {
        createAccount(arjun, "Cash", "CASH", 0);

        mockMvc.perform(post("/api/accounts")
                .header(HttpHeaders.AUTHORIZATION, arjun)
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountJson("CASH", "CASH", 0)))
                .andExpect(status().isConflict());

        // A different user can use the same name
        createAccount(priya, "Cash", "CASH", 0);
    }

    @Test
    @DisplayName("an account with transactions can't be deleted but can be archived; an empty one can be deleted")
    void accountDeleteAndArchive() throws Exception {
        long used = createAccount(arjun, "Old Bank", "BANK", 0);
        long empty = createAccount(arjun, "Never Used", "WALLET", 0);
        createTransaction(arjun, used, null, -100, "2026-09-01", "Something");

        mockMvc.perform(delete("/api/accounts/" + used).header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isConflict());

        mockMvc.perform(put("/api/accounts/" + used)
                .header(HttpHeaders.AUTHORIZATION, arjun)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "Old Bank", "type": "BANK", "openingBalancePaise": 0, "archived": true}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.archived").value(true));

        mockMvc.perform(delete("/api/accounts/" + empty).header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("ledger endpoints need a logged-in user")
    void endpointsRequireLogin() throws Exception {
        mockMvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/transactions")).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/categories")).andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ transactions: ownership

    @Test
    @DisplayName("users can't see, edit or delete each other's transactions")
    void transactionsArePrivate() throws Exception {
        long hdfc = createAccount(arjun, "HDFC Savings", "BANK", 0);
        long txn = createTransaction(arjun, hdfc, null, -20_000, "2026-09-03", "Uber");

        mockMvc.perform(get("/api/transactions").header(HttpHeaders.AUTHORIZATION, priya))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0));

        mockMvc.perform(get("/api/transactions/" + txn).header(HttpHeaders.AUTHORIZATION, priya))
                .andExpect(status().isNotFound());

        long priyasAccount = createAccount(priya, "Priya Bank", "BANK", 0);
        mockMvc.perform(put("/api/transactions/" + txn)
                .header(HttpHeaders.AUTHORIZATION, priya)
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson(priyasAccount, null, -1, "2026-09-03", "Hijack")))
                .andExpect(status().isNotFound());

        mockMvc.perform(delete("/api/transactions/" + txn).header(HttpHeaders.AUTHORIZATION, priya))
                .andExpect(status().isNotFound());

        // Still there for Arjun
        mockMvc.perform(get("/api/transactions/" + txn).header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value("Uber"));
    }

    @Test
    @DisplayName("a transaction can't be put into someone else's account")
    void cannotUseAnotherUsersAccount() throws Exception {
        long arjunsAccount = createAccount(arjun, "HDFC Savings", "BANK", 0);

        mockMvc.perform(post("/api/transactions")
                .header(HttpHeaders.AUTHORIZATION, priya)
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson(arjunsAccount, null, -500, "2026-09-03", "Sneaky")))
                .andExpect(status().isNotFound());

        assertThat(balanceOf(arjun, arjunsAccount)).isZero();
    }

    // ------------------------------------------------------------------ transactions: validation

    @Test
    @DisplayName("missing fields and a zero amount are rejected")
    void transactionValidation() throws Exception {
        mockMvc.perform(post("/api/transactions")
                .header(HttpHeaders.AUTHORIZATION, arjun)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.accountId").exists())
                .andExpect(jsonPath("$.errors.amountPaise").exists())
                .andExpect(jsonPath("$.errors.date").exists())
                .andExpect(jsonPath("$.errors.description").exists());

        long hdfc = createAccount(arjun, "HDFC Savings", "BANK", 0);
        mockMvc.perform(post("/api/transactions")
                .header(HttpHeaders.AUTHORIZATION, arjun)
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson(hdfc, null, 0, "2026-09-03", "Nothing")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Amount cannot be zero"));
    }

    // ------------------------------------------------------------------ transactions: list and filters

    @Test
    @DisplayName("the list is newest first, paginated, and every filter narrows it correctly")
    void filtersAndPagination() throws Exception {
        long hdfc = createAccount(arjun, "HDFC Savings", "BANK", 0);
        long cash = createAccount(arjun, "Cash", "CASH", 0);
        long food = categoryId(arjun, "Food & Dining");
        long salary = categoryId(arjun, "Salary");

        createTransaction(arjun, hdfc, salary, 5_000_000, "2026-08-01", "August salary");
        createTransaction(arjun, hdfc, food, -45_000, "2026-08-15", "SWIGGY order");
        createTransaction(arjun, cash, food, -8_000, "2026-09-02", "Chai and samosa");
        createTransaction(arjun, hdfc, null, -99_900, "2026-09-10", "Amazon order");

        // Newest first, 2 per page
        MvcResult firstPage = mockMvc.perform(get("/api/transactions?page=0&size=2")
                .header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(4))
                .andExpect(jsonPath("$.totalPages").value(2))
                .andReturn();
        List<String> firstTwo = JsonPath.read(firstPage.getResponse().getContentAsString(), "$.items[*].description");
        assertThat(firstTwo).containsExactly("Amazon order", "Chai and samosa");

        assertThat(countFor("accountId=" + cash)).isEqualTo(1);
        assertThat(countFor("categoryId=" + food)).isEqualTo(2);
        assertThat(countFor("direction=IN")).isEqualTo(1);
        assertThat(countFor("direction=OUT")).isEqualTo(3);
        assertThat(countFor("from=2026-09-01&to=2026-09-30")).isEqualTo(2);
        assertThat(countFor("q=swiggy")).isEqualTo(1);
        assertThat(countFor("accountId=" + hdfc + "&direction=OUT&from=2026-08-10")).isEqualTo(2);

        // Uncategorised transactions still appear when no category filter is set
        List<String> all = JsonPath.read(body(get("/api/transactions?size=10"), arjun), "$.items[*].description");
        assertThat(all).contains("Amazon order");
    }

    @Test
    @DisplayName("an invalid direction filter is rejected")
    void invalidDirection() throws Exception {
        mockMvc.perform(get("/api/transactions?direction=SIDEWAYS").header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isBadRequest());
    }

    // ------------------------------------------------------------------ transactions: edit and delete

    @Test
    @DisplayName("moving a transaction to another account updates both balances")
    void updateMovesBetweenAccounts() throws Exception {
        long hdfc = createAccount(arjun, "HDFC Savings", "BANK", 100_000);
        long cash = createAccount(arjun, "Cash", "CASH", 50_000);
        long txn = createTransaction(arjun, hdfc, null, -30_000, "2026-09-05", "Groceries");

        mockMvc.perform(put("/api/transactions/" + txn)
                .header(HttpHeaders.AUTHORIZATION, arjun)
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson(cash, categoryId(arjun, "Groceries"), -30_000, "2026-09-05", "Groceries")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountName").value("Cash"))
                .andExpect(jsonPath("$.categoryName").value("Groceries"));

        assertThat(balanceOf(arjun, hdfc)).isEqualTo(100_000);
        assertThat(balanceOf(arjun, cash)).isEqualTo(20_000);
    }

    @Test
    @DisplayName("deleting a transaction restores the balance")
    void deleteRestoresBalance() throws Exception {
        long hdfc = createAccount(arjun, "HDFC Savings", "BANK", 100_000);
        long txn = createTransaction(arjun, hdfc, null, -30_000, "2026-09-05", "Oops");

        mockMvc.perform(delete("/api/transactions/" + txn).header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isNoContent());

        assertThat(balanceOf(arjun, hdfc)).isEqualTo(100_000);
        mockMvc.perform(get("/api/transactions/" + txn).header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ helpers

    /** Registers and logs in, returning a ready-to-use "Bearer ..." header value. */
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

    private static String accountJson(String name, String type, long openingBalancePaise) {
        return """
                {"name": "%s", "type": "%s", "openingBalancePaise": %d}
                """.formatted(name, type, openingBalancePaise);
    }

    private static String transactionJson(long accountId, Long categoryId, long amountPaise, String date, String description) {
        return """
                {"accountId": %d, "categoryId": %s, "amountPaise": %d, "date": "%s", "description": "%s"}
                """.formatted(accountId, categoryId == null ? "null" : categoryId.toString(), amountPaise, date, description);
    }

    private long createAccount(String auth, String name, String type, long openingBalancePaise) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/accounts")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(accountJson(name, type, openingBalancePaise)))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    private long createTransaction(String auth, long accountId, Long categoryId, long amountPaise, String date,
            String description) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/transactions")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content(transactionJson(accountId, categoryId, amountPaise, date, description)))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    private long categoryId(String auth, String name) throws Exception {
        List<Number> ids = JsonPath.read(body(get("/api/categories"), auth), "$[?(@.name == '" + name + "')].id");
        assertThat(ids).hasSize(1);
        return ids.get(0).longValue();
    }

    private long balanceOf(String auth, long accountId) throws Exception {
        List<Number> balances = JsonPath.read(body(get("/api/accounts"), auth),
                "$[?(@.id == " + accountId + ")].balancePaise");
        assertThat(balances).hasSize(1);
        return balances.get(0).longValue();
    }

    private int countFor(String query) throws Exception {
        Number total = JsonPath.read(body(get("/api/transactions?" + query), arjun), "$.totalItems");
        return total.intValue();
    }

    private String body(MockHttpServletRequestBuilder request, String auth) throws Exception {
        return mockMvc.perform(request.header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static long idOf(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }
}
