package com.kaushiksridhar.finledger.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.jayway.jsonpath.JsonPath;
import com.kaushiksridhar.finledger.support.MutableClock;
import com.kaushiksridhar.finledger.support.TestClockConfig;
import com.kaushiksridhar.finledger.user.User;
import com.kaushiksridhar.finledger.user.UserRepository;

import jakarta.servlet.http.Cookie;

/**
 * End-to-end tests of register, login, refresh and logout through the real HTTP layer,
 * Spring Security and the finledger_test database. The clock is fake so we can jump ahead in time.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(TestClockConfig.class)
class AuthFlowTest {

    private static final String EMAIL = "arjun@example.com";
    private static final String PASSWORD = "password123";
    private static final Pattern REFRESH_COOKIE = Pattern.compile(RefreshCookies.NAME + "=([^;]*)");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserSessionRepository sessionRepository;

    @Autowired
    private MutableClock clock;

    @Autowired
    private JwtDecoder jwtDecoder;

    @BeforeEach
    void resetState() {
        sessionRepository.deleteAll();
        userRepository.deleteAll();
        clock.setInstant(Instant.now());
    }

    // ------------------------------------------------------------------ register

    @Test
    @DisplayName("register creates a user, lower-cases the email and never returns the password hash")
    void registerCreatesUser() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("Arjun Mehta", "Arjun@Example.com", PASSWORD)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Arjun Mehta"))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        User saved = userRepository.findByEmail(EMAIL).orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("$2a$").isNotEqualTo(PASSWORD);
    }

    @Test
    @DisplayName("register rejects an email that already exists, ignoring capitals")
    void registerRejectsDuplicateEmail() throws Exception {
        register();

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("Someone Else", "ARJUN@example.com", PASSWORD)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("An account with this email already exists"));
    }

    @Test
    @DisplayName("register returns 400 with an error for every invalid field")
    void registerValidatesInput() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("", "not-an-email", "short")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());
    }

    // ------------------------------------------------------------------ login

    @Test
    @DisplayName("login returns a 15 minute access token and an HttpOnly refresh cookie")
    void loginIssuesTokens() throws Exception {
        register();
        Long userId = userRepository.findByEmail(EMAIL).orElseThrow().getId();

        MvcResult result = login(EMAIL, PASSWORD);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie)
                .contains(RefreshCookies.NAME + "=")
                .containsIgnoringCase("HttpOnly")
                .containsIgnoringCase("SameSite=Strict")
                .containsIgnoringCase("Path=/api/auth");

        // The refresh token only ever travels in the cookie, never in the JSON body
        String refreshToken = refreshTokenFrom(result);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(refreshToken);

        Jwt jwt = jwtDecoder.decode(accessTokenFrom(result));
        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(userId));
        assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("wrong password and unknown email give the same 401 message")
    void loginFailuresLookIdentical() throws Exception {
        register();

        MvcResult wrongPassword = login(EMAIL, "wrongpassword");
        MvcResult unknownEmail = login("nobody@example.com", PASSWORD);

        assertThat(wrongPassword.getResponse().getStatus()).isEqualTo(401);
        assertThat(unknownEmail.getResponse().getStatus()).isEqualTo(401);
        assertThat(detailOf(wrongPassword)).isEqualTo(detailOf(unknownEmail));
    }

    // ------------------------------------------------------------------ protected endpoints

    @Test
    @DisplayName("/api/users/me works with a valid access token and rejects missing or fake ones")
    void meRequiresValidToken() throws Exception {
        register();
        String accessToken = accessTokenFrom(login(EMAIL, PASSWORD));

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(EMAIL));

        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer this.is.fake"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ refresh

    @Test
    @DisplayName("refresh returns a new access token and rotates the refresh token within the same session")
    void refreshRotatesToken() throws Exception {
        String first = registerAndLogin();

        MvcResult result = refresh(first);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(accessTokenFrom(result)).isNotBlank();

        String second = refreshTokenFrom(result);
        assertThat(second).isNotBlank().isNotEqualTo(first);
        assertThat(sessionIdOf(second)).isEqualTo(sessionIdOf(first));
    }

    @Test
    @DisplayName("the just-replaced token still works for 30 seconds (two tabs) without issuing a new cookie")
    void previousTokenAcceptedWithinGrace() throws Exception {
        String first = registerAndLogin();
        refresh(first);

        clock.advance(Duration.ofSeconds(10));
        MvcResult result = refresh(first);

        assertThat(result.getResponse().getStatus()).isEqualTo(200);
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
        assertThat(sessionRepository.findById(sessionIdOf(first)).orElseThrow().getRevokedAt()).isNull();
    }

    @Test
    @DisplayName("the replaced token used after the grace window revokes the whole session")
    void previousTokenAfterGraceRevokesSession() throws Exception {
        String first = registerAndLogin();
        String second = refreshTokenFrom(refresh(first));

        clock.advance(Duration.ofSeconds(31));

        assertThat(refresh(first).getResponse().getStatus()).isEqualTo(401);
        assertThat(sessionRepository.findById(sessionIdOf(first)).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(refresh(second).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("replaying a token two rotations old is treated as theft: the session is revoked for everyone")
    void oldTokenReuseRevokesSession() throws Exception {
        String first = registerAndLogin();
        String second = refreshTokenFrom(refresh(first));
        String third = refreshTokenFrom(refresh(second));

        MvcResult replay = refresh(first);

        assertThat(replay.getResponse().getStatus()).isEqualTo(401);
        assertThat(replay.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        assertThat(sessionRepository.findById(sessionIdOf(first)).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(refresh(third).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("a session older than 30 days can no longer be refreshed")
    void expiredSessionCannotRefresh() throws Exception {
        String first = registerAndLogin();

        clock.advance(Duration.ofDays(30).plusSeconds(1));

        assertThat(refresh(first).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("refresh without a cookie or with a malformed one is rejected")
    void refreshRejectsMissingOrMalformedToken() throws Exception {
        assertThat(refresh(null).getResponse().getStatus()).isEqualTo(401);
        assertThat(refresh("garbage").getResponse().getStatus()).isEqualTo(401);
        assertThat(refresh("abc.def").getResponse().getStatus()).isEqualTo(401);
        assertThat(refresh("999999.def").getResponse().getStatus()).isEqualTo(401);
    }

    // ------------------------------------------------------------------ logout

    @Test
    @DisplayName("logout revokes the session, clears the cookie, and the token stops working")
    void logoutRevokesSession() throws Exception {
        String token = registerAndLogin();

        MvcResult result = logout(token);

        assertThat(result.getResponse().getStatus()).isEqualTo(204);
        assertThat(result.getResponse().getHeader(HttpHeaders.SET_COOKIE)).contains("Max-Age=0");
        assertThat(sessionRepository.findById(sessionIdOf(token)).orElseThrow().getRevokedAt()).isNotNull();
        assertThat(refresh(token).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    @DisplayName("logout without a cookie is harmless")
    void logoutWithoutCookie() throws Exception {
        assertThat(logout(null).getResponse().getStatus()).isEqualTo(204);
    }

    @Test
    @DisplayName("logout with a real session id but the wrong secret does not end that session")
    void logoutNeedsTheRealSecret() throws Exception {
        String token = registerAndLogin();
        long sessionId = sessionIdOf(token);

        logout(sessionId + ".not-the-real-secret");

        assertThat(sessionRepository.findById(sessionId).orElseThrow().getRevokedAt()).isNull();
        assertThat(refresh(token).getResponse().getStatus()).isEqualTo(200);
    }

    // ------------------------------------------------------------------ helpers

    private static String registerJson(String name, String email, String password) {
        return """
                {"name": "%s", "email": "%s", "password": "%s"}
                """.formatted(name, email, password);
    }

    private void register() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(registerJson("Arjun Mehta", EMAIL, PASSWORD)))
                .andExpect(status().isCreated());
    }

    private MvcResult login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email": "%s", "password": "%s"}
                        """.formatted(email, password)))
                .andReturn();
    }

    /** Registers Arjun, logs in, and returns the refresh token from the cookie. */
    private String registerAndLogin() throws Exception {
        register();
        return refreshTokenFrom(login(EMAIL, PASSWORD));
    }

    private MvcResult refresh(String refreshToken) throws Exception {
        return mockMvc.perform(withCookie(post("/api/auth/refresh"), refreshToken)).andReturn();
    }

    private MvcResult logout(String refreshToken) throws Exception {
        return mockMvc.perform(withCookie(post("/api/auth/logout"), refreshToken)).andReturn();
    }

    private static MockHttpServletRequestBuilder withCookie(MockHttpServletRequestBuilder request, String refreshToken) {
        if (refreshToken != null) {
            request.cookie(new Cookie(RefreshCookies.NAME, refreshToken));
        }
        return request;
    }

    private static String refreshTokenFrom(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        if (header == null) {
            return null;
        }
        Matcher matcher = REFRESH_COOKIE.matcher(header);
        return matcher.find() ? matcher.group(1) : null;
    }

    private static String accessTokenFrom(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.accessToken");
    }

    private static String detailOf(MvcResult result) throws Exception {
        return JsonPath.read(result.getResponse().getContentAsString(), "$.detail");
    }

    private static long sessionIdOf(String refreshToken) {
        return Long.parseLong(refreshToken.substring(0, refreshToken.indexOf('.')));
    }
}
