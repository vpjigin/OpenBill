package app.billing.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** Stricter lockout for sign-in on the public tunnel (3 failures → 15 minutes). */
@Component
public class PublicLoginThrottle {

    static final int MAX_FAILURES = 3;
    static final Duration LOCK = Duration.ofMinutes(15);

    private record Entry(int failures, Instant lockedUntil) {
    }

    private final Clock clock;
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

    public PublicLoginThrottle() {
        this(Clock.systemUTC());
    }

    PublicLoginThrottle(Clock clock) {
        this.clock = clock;
    }

    public boolean isLocked(String username, String ip) {
        return locked(key("u", username)) || locked(key("ip", ip));
    }

    public void failure(String username, String ip) {
        bump(key("u", username));
        bump(key("ip", ip));
    }

    public void success(String username, String ip) {
        entries.remove(key("u", username));
        entries.remove(key("ip", ip));
    }

    private boolean locked(String key) {
        Entry e = entries.get(key);
        return e != null && e.lockedUntil() != null && clock.instant().isBefore(e.lockedUntil());
    }

    private void bump(String key) {
        entries.compute(key, (k, e) -> {
            Instant now = clock.instant();
            int failures = (e == null || (e.lockedUntil() != null && !now.isBefore(e.lockedUntil())))
                    ? 1 : e.failures() + 1;
            Instant until = failures >= MAX_FAILURES ? now.plus(LOCK) : null;
            return new Entry(failures, until);
        });
    }

    private static String key(String kind, String value) {
        return kind + ":" + (value == null ? "" : value.toLowerCase(Locale.ROOT));
    }
}
