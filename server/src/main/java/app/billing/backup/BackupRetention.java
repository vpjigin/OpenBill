package app.billing.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class BackupRetention {

    static final int MAX_REGULAR = 30;
    static final long PRE_RESTORE_DAYS = 7;

    private BackupRetention() {
    }

    static void prune(Path backupsDir, Instant now) throws IOException {
        if (!Files.isDirectory(backupsDir)) {
            return;
        }
        List<Path> regular = new ArrayList<>();
        List<Path> preRestore = new ArrayList<>();
        try (var stream = Files.list(backupsDir)) {
            for (Path p : stream.toList()) {
                if (!Files.isRegularFile(p)) {
                    continue;
                }
                String n = p.getFileName().toString();
                if (!n.endsWith(".db")) {
                    continue;
                }
                if (n.startsWith("pre-restore-")) {
                    preRestore.add(p);
                } else if (n.startsWith("billing-") || n.startsWith("uploaded-")) {
                    regular.add(p);
                }
            }
        }

        regular.sort(Comparator.comparing(BackupRetention::lastModified).reversed());
        for (int i = MAX_REGULAR; i < regular.size(); i++) {
            Files.deleteIfExists(regular.get(i));
        }

        Instant cutoff = now.minus(PRE_RESTORE_DAYS, ChronoUnit.DAYS);
        for (Path p : preRestore) {
            if (lastModified(p).isBefore(cutoff)) {
                Files.deleteIfExists(p);
            }
        }
    }

    private static Instant lastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toInstant();
        } catch (IOException e) {
            return Instant.EPOCH;
        }
    }
}
