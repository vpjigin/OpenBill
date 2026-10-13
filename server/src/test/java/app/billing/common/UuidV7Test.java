package app.billing.common;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.UUID;

import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    void hasVersion7AndRfcVariant() {
        UUID u = UuidV7.generate();
        assertThat(u.version()).isEqualTo(7);
        assertThat(u.variant()).isEqualTo(2);
    }

    @Test
    void embedsCurrentTimeAndIsUnique() {
        long before = System.currentTimeMillis();
        UUID u = UuidV7.generate();
        long ts = u.getMostSignificantBits() >>> 16;
        assertThat(ts).isBetween(before, System.currentTimeMillis());

        var seen = new HashSet<UUID>();
        for (int i = 0; i < 10_000; i++) {
            assertThat(seen.add(UuidV7.generate())).isTrue();
        }
    }
}
