package app.billing.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Best-effort session heartbeat in a short separate transaction. */
@Service
public class AuthTokenTouchService {

    private static final Logger log = LoggerFactory.getLogger(AuthTokenTouchService.class);
    static final Duration SEEN_WRITE_INTERVAL = Duration.ofMinutes(1);

    private final JdbcTemplate jdbc;

    public AuthTokenTouchService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Called outside read-only auth transactions (e.g. from {@link TokenAuthFilter}). */
    public void tryTouchLastSeen(UUID tokenId, Instant lastSeenAt, Instant now) {
        if (!lastSeenAt.plus(SEEN_WRITE_INTERVAL).isBefore(now)) {
            return;
        }
        try {
            touchLastSeen(tokenId, now);
        } catch (Exception e) {
            log.debug("Could not update auth_token last_seen_at for {}: {}", tokenId, e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void touchLastSeen(UUID tokenId, Instant now) {
        Instant threshold = now.minus(SEEN_WRITE_INTERVAL);
        jdbc.update("""
                UPDATE auth_token SET last_seen_at = ?
                WHERE id = ? AND last_seen_at < ?
                """, now.toEpochMilli(), tokenId.toString(), threshold.toEpochMilli());
    }
}
