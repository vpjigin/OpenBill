package app.billing.channel;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;

import app.billing.config.AppProperties;

/** Resolves the cloudflared executable on this machine. */
public final class CloudflaredLocator {

    private CloudflaredLocator() {
    }

    public static String find(AppProperties props) {
        boolean windows = System.getProperty("os.name").toLowerCase(Locale.ROOT).contains("win");
        String name = windows ? "cloudflared.exe" : "cloudflared";

        if (props.cloudflaredPath() != null && !props.cloudflaredPath().isBlank()) {
            Path p = Path.of(props.cloudflaredPath());
            if (Files.isRegularFile(p)) {
                return p.toString();
            }
            return null;
        }
        if (props.binDir() != null && !props.binDir().isBlank()) {
            Path bundled = Path.of(props.binDir(), name);
            if (Files.isRegularFile(bundled)) {
                return bundled.toString();
            }
        }
        Path dataBin = Path.of(props.dataDir(), "bin", name);
        if (Files.isRegularFile(dataBin)) {
            return dataBin.toString();
        }
        String pathEnv = System.getenv("PATH");
        if (pathEnv != null) {
            for (String dir : pathEnv.split(File.pathSeparator)) {
                Path candidate = Path.of(dir, name);
                if (Files.isRegularFile(candidate)) {
                    return candidate.toString();
                }
            }
        }
        return null;
    }
}
