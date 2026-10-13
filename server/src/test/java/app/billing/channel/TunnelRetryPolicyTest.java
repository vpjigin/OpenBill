package app.billing.channel;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class TunnelRetryPolicyTest {

    @Test
    void backoffIncreasesThenCapsAtTwoMinutes() {
        var clock = Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC);
        TunnelRetryPolicy p = new TunnelRetryPolicy(clock);

        assertThat(p.scheduleNextRetryMs()).isEqualTo(5_000);
        assertThat(p.scheduleNextRetryMs()).isEqualTo(15_000);
        assertThat(p.scheduleNextRetryMs()).isEqualTo(60_000);
        assertThat(p.scheduleNextRetryMs()).isEqualTo(120_000);
        assertThat(p.scheduleNextRetryMs()).isEqualTo(120_000);

        assertThat(p.nextRetryAt()).isEqualTo(Instant.parse("2026-01-01T00:02:00Z"));
        p.reset();
        assertThat(p.failureCount()).isZero();
    }
}
