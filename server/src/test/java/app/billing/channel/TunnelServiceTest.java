package app.billing.channel;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class TunnelServiceTest {

    @Test
    void detectsNoInternetLines() {
        assertThat(TunnelService.isNoInternetLine("failed to lookup DNS")).isTrue();
        assertThat(TunnelService.isNoInternetLine("Registered tunnel connection")).isFalse();
    }
}
