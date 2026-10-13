package app.billing;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.scheduling.annotation.EnableScheduling;

import app.billing.backup.RestoreApplier;

@SpringBootApplication
@ConfigurationPropertiesScan
@EnableScheduling
public class BillingApplication {

    public static void main(String[] args) throws Exception {
        // The SQLite file and logs live here; it must exist before the DataSource starts.
        String dataDir = System.getenv().getOrDefault("APP_DATA_DIR",
                Path.of(System.getProperty("user.home"), ".billing-app").toString());
        Path dataPath = Path.of(dataDir);
        Files.createDirectories(dataPath.resolve("logs"));
        if (RestoreApplier.applyIfPending(dataPath)) {
            System.out.println("Applied pending database restore from backup.");
        }
        System.setProperty("app.data-dir", dataDir);

        ConfigurableApplicationContext ctx = SpringApplication.run(BillingApplication.class, args);
        watchParentProcess(ctx);
    }

    /**
     * When launched by the desktop shell, exit if the shell dies (e.g. it crashed),
     * so an orphaned server never keeps holding the ports.
     */
    private static void watchParentProcess(ConfigurableApplicationContext ctx) {
        String pid = System.getenv("APP_PARENT_PID");
        if (pid == null || pid.isBlank()) {
            return;
        }
        long parentPid = Long.parseLong(pid.trim());
        var scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "parent-watchdog");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            boolean alive = ProcessHandle.of(parentPid).map(ProcessHandle::isAlive).orElse(false);
            if (!alive) {
                int code = SpringApplication.exit(ctx, () -> 0);
                System.exit(code);
            }
        }, 5, 5, TimeUnit.SECONDS);
    }
}
