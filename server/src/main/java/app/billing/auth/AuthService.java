package app.billing.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.billing.activity.ActivityActions;
import app.billing.activity.ActivityService;
import app.billing.channel.Channel;
import app.billing.common.ApiException;
import app.billing.user.Role;
import app.billing.user.User;
import app.billing.user.UserRepository;

@Service
public class AuthService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UserRepository users;
    private final AuthTokenRepository tokens;
    private final PasswordEncoder encoder;
    private final LoginThrottle throttle;
    private final PublicLoginThrottle publicThrottle;
    private final ActivityService activity;

    public AuthService(UserRepository users, AuthTokenRepository tokens, PasswordEncoder encoder,
                       LoginThrottle throttle, PublicLoginThrottle publicThrottle, ActivityService activity) {
        this.users = users;
        this.tokens = tokens;
        this.encoder = encoder;
        this.throttle = throttle;
        this.publicThrottle = publicThrottle;
        this.activity = activity;
    }

    public record LoginResult(String token, CurrentUser user) {
    }

    @Transactional
    public LoginResult login(String username, String password, Channel channel, String ip, String userAgent) {
        if (channel == Channel.PUBLIC) {
            if (publicThrottle.isLocked(username, ip)) {
                throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                        "Too many wrong attempts. Try again in 15 minutes.");
            }
        } else if (throttle.isLocked(username, ip)) {
            throw new ApiException(org.springframework.http.HttpStatus.TOO_MANY_REQUESTS,
                    "Too many wrong attempts. Try again in a few minutes.");
        }
        Optional<User> found = users.findByUsernameIgnoreCaseAndDeletedAtIsNull(username.trim());
        if (found.isEmpty() || !encoder.matches(password, found.get().getPasswordHash())) {
            if (channel == Channel.PUBLIC) {
                publicThrottle.failure(username, ip);
            } else {
                throttle.failure(username, ip);
            }
            activity.logLoginFailed(username.trim(), channel, ip);
            throw ApiException.unauthorized("Wrong username or password");
        }
        User u = found.get();
        if (!u.isActive()) {
            throw ApiException.forbidden("This user is disabled");
        }
        if (channel == Channel.ADMIN && u.getRole() != Role.ADMIN) {
            throw ApiException.forbidden("Only admins can use the desktop admin app");
        }
        if (channel.isRemote() && !u.isWebAccess()) {
            throw ApiException.forbidden("Web sign-in is not allowed for this user. Ask the admin.");
        }
        if (channel == Channel.PUBLIC) {
            publicThrottle.success(username, ip);
        } else {
            throttle.success(username, ip);
        }

        String raw = newToken();
        Instant now = Instant.now();
        AuthToken t = new AuthToken();
        t.setTokenHash(hash(raw));
        t.setUser(u);
        t.setChannel(channel);
        t.setIp(ip);
        t.setUserAgent(userAgent == null ? null : userAgent.substring(0, Math.min(200, userAgent.length())));
        t.setExpiresAt(now.plus(lifetime(channel)));
        t.setLastSeenAt(now);
        tokens.save(t);
        u.setLastLoginAt(now);

        activity.log(ActivityActions.LOGIN, u.getUsername(), null, u.getId(), u.getName(), channel, ip);
        return new LoginResult(raw, toCurrent(t));
    }

    /** Validates a bearer token for the channel the request arrived on (read-only, no writes). */
    @Transactional(readOnly = true)
    public Optional<AuthenticatedSession> authenticate(String rawToken, Channel channel) {
        Instant now = Instant.now();
        return tokens.findByTokenHash(hash(rawToken))
                .filter(t -> t.getExpiresAt().isAfter(now))
                .filter(t -> t.getChannel() == channel)      // a Wi-Fi token can't be replayed on admin, etc.
                .filter(t -> t.getUser().isActive() && !t.getUser().isDeleted())
                .map(t -> new AuthenticatedSession(toCurrent(t), t.getLastSeenAt()));
    }

    @Transactional
    public void logout(UUID tokenId) {
        tokens.deleteById(tokenId);
    }

    @Transactional(readOnly = true)
    public List<AuthToken> activeSessions() {
        return tokens.findByExpiresAtAfterOrderByLastSeenAtDesc(Instant.now());
    }

    @Transactional
    public void revoke(UUID tokenId) {
        tokens.deleteById(tokenId);
    }

    @Transactional
    public void revokeChannel(Channel channel) {
        tokens.deleteByChannel(channel);
    }

    @Transactional
    public void revokeUser(UUID userId) {
        tokens.deleteByUserId(userId);
    }

    /** Sign out every device except the one that just changed password. */
    @Transactional
    public void revokeOtherSessions(UUID userId, UUID keepTokenId) {
        tokens.deleteByUserIdAndIdNot(userId, keepTokenId);
    }

    @Transactional
    public void revokeUserRemote(UUID userId) {
        tokens.deleteByUserIdAndChannelIn(userId, List.of(Channel.LAN, Channel.PUBLIC));
    }

    @Scheduled(fixedDelay = 3_600_000)
    @Transactional
    public void cleanup() {
        tokens.deleteExpired(Instant.now());
    }

    private static Duration lifetime(Channel channel) {
        return switch (channel) {
            case ADMIN -> Duration.ofDays(30);
            case LOCAL, LAN -> Duration.ofHours(16);
            case PUBLIC -> Duration.ofHours(8);
        };
    }

    private static CurrentUser toCurrent(AuthToken t) {
        User u = t.getUser();
        return new CurrentUser(u.getId(), t.getId(), u.getName(), u.getUsername(), u.getRole(), t.getChannel());
    }

    private static String newToken() {
        byte[] b = new byte[32];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String hash(String raw) {
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(raw.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(d);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
