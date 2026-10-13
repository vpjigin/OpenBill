package app.billing.channel;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import app.billing.config.AppProperties;

class CloudflaredLocatorTest {

    @TempDir
    Path temp;

    @Test
    void prefersExplicitPathThenBinDirThenDataDir(@TempDir Path bin, @TempDir Path data) throws Exception {
        Path explicit = temp.resolve("custom-cloudflared");
        Files.writeString(explicit, "x");
        Path bundled = bin.resolve("cloudflared");
        Files.writeString(bundled, "y");
        Path dataBin = data.resolve("bin").resolve("cloudflared");
        Files.createDirectories(dataBin.getParent());
        Files.writeString(dataBin, "z");

        AppProperties props = new AppProperties("OpenBill", data.toString(), 9090, 8080, 8081, explicit.toString(),
                null, bin.toString());
        assertThat(CloudflaredLocator.find(props)).isEqualTo(explicit.toString());

        AppProperties noExplicit = new AppProperties("OpenBill", data.toString(), 9090, 8080, 8081, "", null,
                bin.toString());
        assertThat(CloudflaredLocator.find(noExplicit)).isEqualTo(bundled.toString());

        AppProperties noBin = new AppProperties("OpenBill", data.toString(), 9090, 8080, 8081, "", null, "");
        assertThat(CloudflaredLocator.find(noBin)).isEqualTo(dataBin.toString());
    }
}
