package com.kaushiksridhar.finledger.support;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.jayway.jsonpath.JsonPath;

/** Small helpers shared by the integration tests: sign up, create accounts and transactions, read JSON. */
public final class ApiTestClient {

    private final MockMvc mockMvc;

    public ApiTestClient(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    /** Registers and logs in, returning a ready-to-use "Bearer ..." header value. */
    public String signUp(String email) throws Exception {
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

    public long createAccount(String auth, String name) throws Exception {
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

    public long categoryId(String auth, String name) throws Exception {
        List<Number> ids = JsonPath.read(body(auth, "/api/categories"), "$[?(@.name == '" + name + "')].id");
        return ids.get(0).longValue();
    }

    public void addTransaction(String auth, long accountId, Long categoryId, long amountPaise, LocalDate date,
            String description, String merchant) throws Exception {
        mockMvc.perform(post("/api/transactions")
                .header(HttpHeaders.AUTHORIZATION, auth)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"accountId": %d, "categoryId": %s, "amountPaise": %d, "date": "%s",
                         "description": "%s", "merchant": %s}
                        """.formatted(accountId, categoryId == null ? "null" : categoryId.toString(), amountPaise,
                        date, description, merchant == null ? "null" : "\"" + merchant + "\"")))
                .andExpect(status().isCreated());
    }

    public String body(String auth, String url) throws Exception {
        return mockMvc.perform(get(url).header(HttpHeaders.AUTHORIZATION, auth))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    public static long idOf(MvcResult result) throws Exception {
        Number id = JsonPath.read(result.getResponse().getContentAsString(), "$.id");
        return id.longValue();
    }

    public static <T> T read(String json, String path) {
        return JsonPath.read(json, path);
    }

    public static long number(String json, String path) {
        Number value = JsonPath.read(json, path);
        return value.longValue();
    }
}
