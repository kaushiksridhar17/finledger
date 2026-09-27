package com.kaushiksridhar.finledger.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.Locale;
import java.util.UUID;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.kaushiksridhar.finledger.common.ApiException;
import com.kaushiksridhar.finledger.security.AccessTokenService;
import com.kaushiksridhar.finledger.security.AccessTokenService.AccessToken;
import com.kaushiksridhar.finledger.security.AuthProperties;
import com.kaushiksridhar.finledger.user.User;
import com.kaushiksridhar.finledger.user.UserRepository;

@Service
public class AuthService {

    private final UserRepository userRepository;
    private final UserSessionRepository sessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokenService;
    private final AuthProperties properties;
    private final Clock clock;

    // Compared against when the email doesn't exist, so both failure cases take the same time
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository,
            UserSessionRepository sessionRepository,
            PasswordEncoder passwordEncoder,
            AccessTokenService accessTokenService,
            AuthProperties properties,
            Clock clock) {
        this.userRepository = userRepository;
        this.sessionRepository = sessionRepository;
        this.passwordEncoder = passwordEncoder;
        this.accessTokenService = accessTokenService;
        this.properties = properties;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode(UUID.randomUUID().toString());
    }

    @Transactional
    public User register(RegisterRequest request) {
        String email = normalizeEmail(request.email());

        if (userRepository.existsByEmail(email)) {
            throw emailTaken();
        }

        User user = new User(request.name().trim(), email, passwordEncoder.encode(request.password()));

        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // Two sign-ups with the same email at the same moment: the unique constraint wins
            throw emailTaken();
        }
    }

    @Transactional
    public LoginResult login(LoginRequest request, String userAgent) {
        String email = normalizeEmail(request.email());
        User user = userRepository.findByEmail(email).orElse(null);

        String hashToCheck = (user != null) ? user.getPasswordHash() : dummyPasswordHash;
        boolean passwordMatches = passwordEncoder.matches(request.password(), hashToCheck);

        if (user == null || !passwordMatches) {
            throw invalidCredentials();
        }

        return startSession(user, userAgent);
    }

    private LoginResult startSession(User user, String userAgent) {
        Instant now = clock.instant();
        String secret = RefreshTokens.newSecret();

        UserSession session = new UserSession();
        session.setUser(user);
        session.setRefreshTokenHash(RefreshTokens.hash(secret));
        session.setUserAgent(truncate(userAgent, 255));
        session.setCreatedAt(now);
        session.setLastUsedAt(now);
        session.setExpiresAt(now.plus(properties.refreshTokenTtl()));
        sessionRepository.save(session);

        AccessToken accessToken = accessTokenService.issue(user);
        String refreshToken = session.getId() + "." + secret;

        return new LoginResult(user, accessToken.value(), accessToken.expiresAt(),
                refreshToken, session.getExpiresAt());
    }

    static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }

    private static ApiException emailTaken() {
        return new ApiException(HttpStatus.CONFLICT, "An account with this email already exists");
    }

    private static ApiException invalidCredentials() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect");
    }
}