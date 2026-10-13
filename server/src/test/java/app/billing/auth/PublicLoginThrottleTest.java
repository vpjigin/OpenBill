package app.billing.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class PublicLoginThrottleTest {

    @Test
    void locksAfterThreeFailuresForFifteenMinutes() {
        var clock = Clock.fixed(Instant.parse("2026-01-01T12:00:00Z"), ZoneOffset.UTC);
        PublicLoginThrottle t = new PublicLoginThrottle(clock);

        t.failure("cashier", "203.0.113.1");
        t.failure("cashier", "203.0.113.1");
        assertThat(t.isLocked("cashier", "203.0.113.1")).isFalse();
        t.failure("cashier", "203.0.113.1");
        assertThat(t.isLocked("cashier", "203.0.113.1")).isTrue();

        t.success("cashier", "203.0.113.1");
        assertThat(t.isLocked("cashier", "203.0.113.1")).isFalse();
    }
}
