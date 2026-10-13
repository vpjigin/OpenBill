package app.billing.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.logging.Logger;

/**
 * Applies a pending database restore before Spring Boot starts (no datasource open yet).
 */
public final class RestoreApplier {

    private static final Logger LOG = Logger.getLogger(RestoreApplier.class.getName());
    static final String PENDING_FILE = "restore-pending.txt";
    static final String DB_FILE = "billing.db";
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private RestoreApplier() {
    }

    /** @return true if a restore was applied */
    public static boolean applyIfPending(Path dataDir) throws IOException {
        Path pending = dataDir.resolve(PENDING_FILE);
        if (!Files.isRegularFile(pending)) {
            return false;
        }
        String name = Files.readString(pending).trim();
        if (name.isBlank()) {
            Files.deleteIfExists(pending);
            return false;
        }
        BackupService.validateBackupFileName(name);

        Path backupsDir = dataDir.resolve("backups");
        Path source = backupsDir.resolve(name).normalize();
        if (!source.startsWith(backupsDir.normalize()) || !Files.isRegularFile(source)) {
            throw new IOException("Pending restore file not found: " + name);
        }

        Files.createDirectories(backupsDir);
        Path db = dataDir.resolve(DB_FILE);
        if (Files.isRegularFile(db)) {
            String preName = "pre-restore-" + STAMP.format(LocalDateTime.now(ZONE)) + ".db";
            Path pre = backupsDir.resolve(preName);
            Files.copy(db, pre, StandardCopyOption.REPLACE_EXISTING);
        }

        Files.copy(source, db, StandardCopyOption.REPLACE_EXISTING);
        Files.deleteIfExists(dataDir.resolve(DB_FILE + "-wal"));
        Files.deleteIfExists(dataDir.resolve(DB_FILE + "-shm"));
        Files.deleteIfExists(pending);

        LOG.info("Restored billing database from backup " + name);
        return true;
    }
}
