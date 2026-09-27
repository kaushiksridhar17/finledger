package com.kaushiksridhar.finledger.auth;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.kaushiksridhar.finledger.common.ApiException;
import com.kaushiksridhar.finledger.common.ApiExceptionHandler;
import com.kaushiksridhar.finledger.demo.DemoService;
import com.kaushiksridhar.finledger.user.User;
import com.kaushiksridhar.finledger.user.UserResponse;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final DemoService demoService;
    private final RefreshCookies refreshCookies;

    public AuthController(AuthService authService, DemoService demoService, RefreshCookies refreshCookies) {
        this.authService = authService;
        this.demoService = demoService;
        this.refreshCookies = refreshCookies;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        return UserResponse.from(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {

        return loggedIn(authService.login(request, userAgent));
    }

    /** "Try the demo": creates a fresh demo user with a year of data and logs straight in as them. */
    @PostMapping("/demo")
    public ResponseEntity<AuthResponse> demo(
            @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent) {

        demoService.prepare();   // anything slow (fetching fund prices) happens before the database transaction
        User demoUser = demoService.createDemoUser();
        return loggedIn(authService.startSession(demoUser, userAgent));
    }

    /**
     * Reads the refresh cookie and returns a new access token.
     * On failure the cookie is also cleared, so the browser doesn't keep sending a dead token.
     */
    @PostMapping("/refresh")
    public ResponseEntity<Object> refresh(
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {

        try {
            LoginResult result = authService.refresh(refreshToken);

            ResponseEntity.BodyBuilder response = ResponseEntity.ok();
            if (result.refreshToken() != null) {
                response.header(HttpHeaders.SET_COOKIE,
                        refreshCookies.create(result.refreshToken(), result.refreshTokenExpiresAt()).toString());
            }
            return response.body(AuthResponse.from(result));

        } catch (ApiException e) {
            return ResponseEntity.status(e.getStatus())
                    .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
                    .body(ApiExceptionHandler.toProblem(e));
        }
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @CookieValue(name = RefreshCookies.NAME, required = false) String refreshToken) {

        authService.logout(refreshToken);

        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, refreshCookies.clear().toString())
                .build();
    }

    // Access token in the body, refresh token in the HttpOnly cookie
    private ResponseEntity<AuthResponse> loggedIn(LoginResult result) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        refreshCookies.create(result.refreshToken(), result.refreshTokenExpiresAt()).toString())
                .body(AuthResponse.from(result));
    }
}
