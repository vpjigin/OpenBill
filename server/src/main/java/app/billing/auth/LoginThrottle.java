package app.billing.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/** Locks a username (and an IP) for a while after repeated wrong passwords. In memory on purpose. */
@Component
public class LoginThrottle {

    private static final int MAX_FAILURES = 5;
    private static final Duration LOCK = Duration.ofMinutes(5);

    private record Entry(int failures, Instant lockedUntil) {
    }

    private final Map<String, Entry> entries = new ConcurrentHashMap<>();

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
        return e != null && e.lockedUntil() != null && Instant.now().isBefore(e.lockedUntil());
    }

    private void bump(String key) {
        entries.compute(key, (k, e) -> {
            int failures = (e == null || (e.lockedUntil() != null && Instant.now().isAfter(e.lockedUntil())))
                    ? 1 : e.failures() + 1;
            Instant until = failures >= MAX_FAILURES ? Instant.now().plus(LOCK) : null;
            return new Entry(failures, until);
        });
    }

    private static String key(String kind, String value) {
        return kind + ":" + (value == null ? "" : value.toLowerCase(Locale.ROOT));
    }
}
