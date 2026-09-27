package com.kaushiksridhar.finledger.importing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
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
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

/**
 * Uploads real-looking statements through the HTTP API and waits for the background import to finish.
 * Proves: rows land with categories, bad lines are reported, re-uploads and overlapping statements
 * don't create duplicates, user rules apply, and nobody can import into someone else's account.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ImportFlowTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private String arjun;
    private long hdfc;

    @BeforeEach
    void setUp() throws Exception {
        jdbcTemplate.update("DELETE FROM users");
        arjun = signUp("arjun@example.com");
        hdfc = createAccount(arjun, "HDFC Savings");
    }

    @Test
    @DisplayName("an upload is accepted straight away, then imported in the background with categories")
    void importsInTheBackground() throws Exception {
        MvcResult accepted = upload(arjun, hdfc, "hdfc-august.csv", resource("hdfc-august.csv"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("QUEUED"))
                .andExpect(jsonPath("$.accountName").value("HDFC Savings"))
                .andReturn();

        String finished = waitUntilFinished(arjun, idOf(accepted));

        assertThat(read(finished, "$.status")).isEqualTo("COMPLETED");
        assertThat(read(finished, "$.bankFormat")).isEqualTo("HDFC");
        assertThat(number(finished, "$.rowsTotal")).isEqualTo(29);
        assertThat(number(finished, "$.rowsImported")).isEqualTo(27);
        assertThat(number(finished, "$.rowsDuplicate")).isZero();
        assertThat(number(finished, "$.rowsFailed")).isEqualTo(2);

        List<Number> errorLines = JsonPath.read(finished, "$.rowErrors[*].lineNumber");
        assertThat(errorLines.stream().map(Number::intValue).toList()).containsExactly(23, 34);

        assertThat(transactionCount(arjun, "")).isEqualTo(27);

        String swiggy = body(arjun, "/api/transactions?q=swiggy&size=50");
        List<String> swiggyCategories = JsonPath.read(swiggy, "$.items[*].categoryName");
        List<String> swiggyMerchants = JsonPath.read(swiggy, "$.items[*].merchant");
        assertThat(swiggyCategories).hasSize(3).containsOnly("Food & Dining");
        assertThat(swiggyMerchants).containsOnly("Swiggy");
    }

    @Test
    @DisplayName("uploading the same statement again imports nothing new")
    void reuploadIsAllDuplicates() throws Exception {
        importAndWait(hdfc, "hdfc-august.csv");
        String second = importAndWait(hdfc, "hdfc-august.csv");

        assertThat(number(second, "$.rowsImported")).isZero();
        assertThat(number(second, "$.rowsDuplicate")).isEqualTo(27);
        assertThat(transactionCount(arjun, "")).isEqualTo(27);
    }

    @Test
    @DisplayName("an overlapping statement only adds the rows that weren't already imported")
    void overlappingStatement() throws Exception {
        importAndWait(hdfc, "hdfc-august.csv");
        String overlap = importAndWait(hdfc, "hdfc-overlap.csv");

        assertThat(number(overlap, "$.rowsImported")).isEqualTo(14);
        assertThat(number(overlap, "$.rowsDuplicate")).isEqualTo(12);
        assertThat(transactionCount(arjun, "")).isEqualTo(27 + 14);
    }

    @Test
    @DisplayName("two identical same-day rows in one file are both imported")
    void identicalRowsBothKept() throws Exception {
        importAndWait(hdfc, "hdfc-august.csv");

        assertThat(transactionCount(arjun, "q=chai")).isEqualTo(2);
    }

    @Test
    @DisplayName("the user's own rules categorise rows the built-in rules don't know")
    void userRulesApply() throws Exception {
        long food = categoryId(arjun, "Food & Dining");
        mockMvc.perform(post("/api/category-rules")
                .header(HttpHeaders.AUTHORIZATION, arjun)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pattern": "chai point", "matchType": "CONTAINS", "categoryId": %d}
                        """.formatted(food)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.pattern").value("CHAI POINT"));

        importAndWait(hdfc, "hdfc-august.csv");

        List<String> categories = JsonPath.read(body(arjun, "/api/transactions?q=chai"), "$.items[*].categoryName");
        assertThat(categories).containsExactly("Food & Dining", "Food & Dining");
    }

    @Test
    @DisplayName("a file that isn't a known statement layout fails with a helpful message")
    void unrecognisedFileFails() throws Exception {
        String result = waitUntilFinished(arjun, idOf(upload(arjun, hdfc, "contacts.csv", "name,phone\nArjun,98450\n")
                .andExpect(status().isAccepted()).andReturn()));

        assertThat(read(result, "$.status")).isEqualTo("FAILED");
        assertThat(read(result, "$.errorMessage")).contains("couldn't recognise");
        assertThat(transactionCount(arjun, "")).isZero();
    }

    @Test
    @DisplayName("non-CSV files, empty files and other people's accounts are refused")
    void rejectsBadUploads() throws Exception {
        upload(arjun, hdfc, "statement.pdf", "%PDF-1.4").andExpect(status().isBadRequest());
        upload(arjun, hdfc, "empty.csv", "").andExpect(status().isBadRequest());

        String priya = signUp("priya@example.com");
        upload(priya, hdfc, "hdfc-august.csv", resource("hdfc-august.csv")).andExpect(status().isNotFound());

        long arjunsImport = idOf(upload(arjun, hdfc, "hdfc-august.csv", resource("hdfc-august.csv")).andReturn());
        waitUntilFinished(arjun, arjunsImport);

        mockMvc.perform(get("/api/imports/" + arjunsImport).header(HttpHeaders.AUTHORIZATION, priya))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/imports").header(HttpHeaders.AUTHORIZATION, priya))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("rules can be listed and deleted only by their owner")
    void rulesBelongToTheirOwner() throws Exception {
        long food = categoryId(arjun, "Food & Dining");
        MvcResult created = mockMvc.perform(post("/api/category-rules")
                .header(HttpHeaders.AUTHORIZATION, arjun)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"pattern": "UPI/CHAI", "matchType": "STARTS_WITH", "categoryId": %d}
                        """.formatted(food)))
                .andExpect(status().isCreated())
                .andReturn();
        long ruleId = idOf(created);

        String priya = signUp("priya@example.com");
        mockMvc.perform(delete("/api/category-rules/" + ruleId).header(HttpHeaders.AUTHORIZATION, priya))
                .andExpect(status().isNotFound());

        mockMvc.perform(get("/api/category-rules").header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].categoryName").value("Food & Dining"));

        mockMvc.perform(delete("/api/category-rules/" + ruleId).header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(status().isNoContent());
        mockMvc.perform(get("/api/category-rules").header(HttpHeaders.AUTHORIZATION, arjun))
                .andExpect(jsonPath("$.length()").value(0));
    }

    // ------------------------------------------------------------------ helpers

    private String importAndWait(long accountId, String fileName) throws Exception {
        MvcResult accepted = upload(arjun, accountId, fileName, resource(fileName))
                .andExpect(status().isAccepted())
                .andReturn();
        String result = waitUntilFinished(arjun, idOf(accepted));
        assertThat(read(result, "$.status")).isEqualTo("COMPLETED");
        return result;
    }

    private ResultActions upload(String auth, long accountId, String fileName, String content) throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", fileName, "text/csv",
                content.getBytes(StandardCharsets.UTF_8));
        return mockMvc.perform(multipart("/api/imports")
                .file(file)
                .param("accountId", String.valueOf(accountId))
                .header(HttpHeaders.AUTHORIZATION, auth));
    }

    /** Polls the import until it's COMPLETED or FAILED, for up to 15 seconds. */
    private String waitUntilFinished(String auth, long importId) throws Exception {
        for (int attempt = 0; attempt < 150; attempt++) {
            String json = body(auth, "/api/imports/" + importId);
            String status = read(json, "$.status");
            if (status.equals("COMPLETED") || status.equals("FAILED")) {
                return json;
            }
            Thread.sleep(100);
        }
        throw new AssertionError("Import " + importId + " didn't finish within 15 seconds");
    }

    private int transactionCount(String auth, String query) throws Exception {
        return number(body(auth, "/api/transactions?size=1&" + query), "$.totalItems");
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

        return "Bearer " + read(login.getResponse().getContentAsString(), "$.accessToken");
    }

    private long createAccount(String auth, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/accounts")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name": "%s", "type": "BANK", "openingBalancePaise": 0}
                        """.formatted(name)))
                .andExpect(status().isCreated())
                .andReturn();
        return idOf(result);
    }

    private long categoryId(String auth, String name) throws Exception {
        List<Number> ids = JsonPath.read(body(auth, "/api/categories"), "$[?(@.name == '" + name + "')].id");
        return ids.get(0).longValue();
    }

    private String body(String auth, String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private static long idOf(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    private static String read(String json, String path) {
        return JsonPath.read(json, path);
    }

    private static int number(String json, String path) {
        Number value = JsonPath.read(json, path);
        return value.intValue();
    }

    private static String resource(String name) throws IOException {
        try (InputStream in = ImportFlowTest.class.getResourceAsStream("/statements/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
