package com.kaushiksridhar.finledger.auth;

import java.time.Clock;
import java.time.Duration;
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

    // How long the just-replaced refresh secret is still accepted (two tabs refreshing at once)
    static final Duration ROTATION_GRACE = Duration.ofSeconds(30);

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

    // ---------------------------------------------------------------- register

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

    // ---------------------------------------------------------------- login

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

    /** Creates a session (a login) for a user who has already been verified. Also used by the demo button. */
    @Transactional
    public LoginResult startSession(User user, String userAgent) {
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

    // ---------------------------------------------------------------- refresh

    /**
     * Swaps a valid refresh token for a new access token and a new refresh token (rotation).
     *
     * Three cases:
     * 1. Current secret: rotate it and return both new tokens.
     * 2. The secret replaced in the last 30 seconds: another tab just refreshed and the browser
     *    already holds the new cookie, so only issue a new access token.
     * 3. Anything older: the token was copied and replayed. Revoke the whole session.
     *
     * noRollbackFor keeps the revocation in case 3 saved even though we then throw an error.
     */
    @Transactional(noRollbackFor = ApiException.class)
    public LoginResult refresh(String refreshToken) {
        RefreshTokens.Parsed token = RefreshTokens.parse(refreshToken).orElseThrow(AuthService::sessionExpired);

        UserSession session = sessionRepository.findByIdForUpdate(token.sessionId())
                .orElseThrow(AuthService::sessionExpired);

        Instant now = clock.instant();
        if (!session.isActive(now)) {
            throw sessionExpired();
        }

        User user = session.getUser();
        String presentedHash = RefreshTokens.hash(token.secret());

        // Case 1: the current secret. Rotate it.
        if (RefreshTokens.matches(presentedHash, session.getRefreshTokenHash())) {
            String newSecret = RefreshTokens.newSecret();
            session.setPreviousRefreshTokenHash(session.getRefreshTokenHash());
            session.setRefreshTokenHash(RefreshTokens.hash(newSecret));
            session.setRotatedAt(now);
            session.setLastUsedAt(now);

            AccessToken accessToken = accessTokenService.issue(user);
            return new LoginResult(user, accessToken.value(), accessToken.expiresAt(),
                    session.getId() + "." + newSecret, session.getExpiresAt());
        }

        // Case 2: the secret we replaced a moment ago. A second tab raced the first one.
        boolean withinGrace = session.getRotatedAt() != null
                && session.getRotatedAt().plus(ROTATION_GRACE).isAfter(now);

        if (withinGrace && RefreshTokens.matches(presentedHash, session.getPreviousRefreshTokenHash())) {
            session.setLastUsedAt(now);

            AccessToken accessToken = accessTokenService.issue(user);
            return new LoginResult(user, accessToken.value(), accessToken.expiresAt(),
                    null, session.getExpiresAt());
        }

        // Case 3: an old secret was replayed. Someone else may hold a copy, so end the session for everyone.
        session.setRevokedAt(now);
        throw sessionExpired();
    }

    // ---------------------------------------------------------------- logout

    /**
     * Revokes the session the cookie belongs to. Always succeeds, even with no cookie or a bad one,
     * so logging out twice is harmless. The hash check stops anyone logging out other people
     * just by guessing session ids.
     */
    @Transactional
    public void logout(String refreshToken) {
        RefreshTokens.parse(refreshToken).ifPresent(token -> sessionRepository.findById(token.sessionId())
                .ifPresent(session -> {
                    String presentedHash = RefreshTokens.hash(token.secret());
                    boolean belongsToSession = RefreshTokens.matches(presentedHash, session.getRefreshTokenHash())
                            || RefreshTokens.matches(presentedHash, session.getPreviousRefreshTokenHash());

                    if (belongsToSession && session.getRevokedAt() == null) {
                        session.setRevokedAt(clock.instant());
                    }
                }));
    }

    // ---------------------------------------------------------------- helpers

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

    private static ApiException sessionExpired() {
        return new ApiException(HttpStatus.UNAUTHORIZED, "Your session has expired. Please log in again.");
    }
}
