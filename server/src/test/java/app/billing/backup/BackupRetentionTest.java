package app.billing.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BackupRetentionTest {

    @TempDir
    Path backupsDir;

    @Test
    void keepsNewest30RegularBackups() throws Exception {
        Instant now = Instant.parse("2026-06-15T12:00:00Z");
        for (int i = 0; i < 35; i++) {
            Path p = backupsDir.resolve("billing-20260601-%02d00.db".formatted(i));
            Files.writeString(p, "x");
            Files.setLastModifiedTime(p, FileTime.from(now.minus(35 - i, ChronoUnit.HOURS)));
        }
        BackupRetention.prune(backupsDir, now);
        long count;
        try (var stream = Files.list(backupsDir)) {
            count = stream.filter(p -> p.getFileName().toString().startsWith("billing-")).count();
        }
        assertThat(count).isEqualTo(30);
    }

    @Test
    void dropsPreRestoreOlderThanSevenDays() throws Exception {
        Instant now = Instant.parse("2026-06-15T12:00:00Z");
        Path old = backupsDir.resolve("pre-restore-20260501-120000.db");
        Path recent = backupsDir.resolve("pre-restore-20260614-120000.db");
        Files.writeString(old, "old");
        Files.writeString(recent, "new");
        Files.setLastModifiedTime(old, FileTime.from(now.minus(10, ChronoUnit.DAYS)));
        Files.setLastModifiedTime(recent, FileTime.from(now.minus(1, ChronoUnit.DAYS)));
        BackupRetention.prune(backupsDir, now);
        assertThat(Files.exists(old)).isFalse();
        assertThat(Files.exists(recent)).isTrue();
    }
}
