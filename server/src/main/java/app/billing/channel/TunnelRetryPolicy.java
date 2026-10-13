package app.billing.channel;

import java.time.Clock;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicInteger;

/** Backoff for restarting cloudflared while public access stays enabled. */
public class TunnelRetryPolicy {

    private static final long[] BACKOFF_MS = {5_000, 15_000, 60_000, 120_000};

    private final Clock clock;
    private final AtomicInteger failures = new AtomicInteger();
    private volatile Instant nextRetryAt;

    public TunnelRetryPolicy() {
        this(Clock.systemUTC());
    }

    TunnelRetryPolicy(Clock clock) {
        this.clock = clock;
    }

    public void reset() {
        failures.set(0);
        nextRetryAt = null;
    }

    /** Call when a launch attempt fails or the process exits unexpectedly. */
    public long scheduleNextRetryMs() {
        int n = failures.getAndIncrement();
        long delay = BACKOFF_MS[Math.min(n, BACKOFF_MS.length - 1)];
        nextRetryAt = clock.instant().plusMillis(delay);
        return delay;
    }

    public Instant nextRetryAt() {
        return nextRetryAt;
    }

    public int failureCount() {
        return failures.get();
    }
}
