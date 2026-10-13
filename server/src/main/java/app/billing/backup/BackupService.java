package app.billing.backup;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import app.billing.common.ApiException;
import app.billing.config.AppProperties;

@Service
public class BackupService {

    private static final Pattern BACKUP_NAME = Pattern.compile("[A-Za-z0-9._-]+\\.db");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final AppProperties props;
    private final JdbcTemplate jdbc;

    public BackupService(AppProperties props, JdbcTemplate jdbc) {
        this.props = props;
        this.jdbc = jdbc;
    }

    public record BackupFile(String name, long size, Instant createdAt, String kind) {
    }

    public record BackupList(List<BackupFile> backups, Instant lastBackupAt, String pendingRestore) {
    }

    public Path backupsDir() {
        return Path.of(props.dataDir(), "backups");
    }

    public Path dbPath() {
        return Path.of(props.dataDir(), "billing.db");
    }

    public Path pendingMarker() {
        return Path.of(props.dataDir(), RestoreApplier.PENDING_FILE);
    }

    public BackupFile createBackup() {
        try {
            Path dir = backupsDir();
            Files.createDirectories(dir);
            String name = "billing-" + STAMP.format(LocalDateTime.now(ZONE)) + ".db";
            Path target = dir.resolve(name);
            vacuumInto(target);
            BackupRetention.prune(dir, Instant.now());
            return toFile(target, "backup");
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Backup failed: " + e.getMessage());
        }
    }

    public BackupList list() {
        Path dir = backupsDir();
        List<BackupFile> files = new ArrayList<>();
        Instant lastBackup = null;
        if (Files.isDirectory(dir)) {
            try (var stream = Files.list(dir)) {
                for (Path p : stream.toList()) {
                    if (!Files.isRegularFile(p)) {
                        continue;
                    }
                    String n = p.getFileName().toString();
                    if (!n.endsWith(".db")) {
                        continue;
                    }
                    String kind = kindOf(n);
                    if (kind == null) {
                        continue;
                    }
                    BackupFile bf = toFile(p, kind);
                    files.add(bf);
                    if ("backup".equals(kind) || "upload".equals(kind)) {
                        if (lastBackup == null || bf.createdAt().isAfter(lastBackup)) {
                            lastBackup = bf.createdAt();
                        }
                    }
                }
            } catch (IOException ignored) {
            }
        }
        files.sort(Comparator.comparing(BackupFile::createdAt).reversed());
        return new BackupList(files, lastBackup, readPending());
    }

    public Instant lastBackupAt() {
        return list().lastBackupAt();
    }

    public BackupFile upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("File is required");
        }
        String original = file.getOriginalFilename();
        if (original != null && (original.contains("..") || original.contains("/") || original.contains("\\"))) {
            throw ApiException.badRequest("Invalid file name");
        }
        try {
            Path dir = backupsDir();
            Files.createDirectories(dir);
            String name = "uploaded-" + STAMP.format(LocalDateTime.now(ZONE)) + ".db";
            Path target = dir.resolve(name);
            try (InputStream in = file.getInputStream()) {
                Files.copy(in, target, StandardCopyOption.REPLACE_EXISTING);
            }
            BackupValidator.validateSqliteBackup(target);
            BackupRetention.prune(dir, Instant.now());
            return toFile(target, "upload");
        } catch (IOException e) {
            throw ApiException.badRequest("Upload failed: " + e.getMessage());
        }
    }

    public void scheduleRestore(String name) {
        validateBackupFileName(name);
        Path file = resolveBackup(name);
        if (!Files.isRegularFile(file)) {
            throw ApiException.badRequest("Backup not found");
        }
        try {
            Files.writeString(pendingMarker(), name.trim());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not schedule restore");
        }
    }

    public void cancelPendingRestore() {
        try {
            Files.deleteIfExists(pendingMarker());
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not cancel restore");
        }
    }

    public void openBackupFolder() {
        Path dir = backupsDir();
        try {
            Files.createDirectories(dir);
            String os = System.getProperty("os.name").toLowerCase();
            List<String> cmd = os.contains("win") ? List.of("explorer", dir.toAbsolutePath().toString())
                    : os.contains("mac") ? List.of("open", dir.toAbsolutePath().toString())
                    : List.of("xdg-open", dir.toAbsolutePath().toString());
            new ProcessBuilder(cmd).start();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not open folder");
        }
    }

    public boolean isBackupStale() {
        Instant last = lastBackupAt();
        if (last == null) {
            return true;
        }
        return last.isBefore(Instant.now().minus(48, ChronoUnit.HOURS));
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 120_000)
    public void scheduledBackupIfNeeded() {
        Instant newest = lastBackupAt();
        if (newest != null && newest.isAfter(Instant.now().minus(24, ChronoUnit.HOURS))) {
            return;
        }
        createBackup();
    }

    public static void validateBackupFileName(String name) {
        if (name == null || !BACKUP_NAME.matcher(name).matches()) {
            throw ApiException.badRequest("Invalid backup name");
        }
        if (name.contains("..") || name.contains("/") || name.contains("\\")) {
            throw ApiException.badRequest("Invalid backup name");
        }
    }

    private Path resolveBackup(String name) {
        validateBackupFileName(name);
        Path dir = backupsDir().normalize();
        Path file = dir.resolve(name).normalize();
        if (!file.startsWith(dir)) {
            throw ApiException.badRequest("Invalid backup name");
        }
        return file;
    }

    private void vacuumInto(Path target) throws IOException {
        Files.deleteIfExists(target);
        String path = target.toAbsolutePath().toString().replace("'", "''");
        jdbc.execute("VACUUM INTO '" + path + "'");
    }

    private static BackupFile toFile(Path p, String kind) throws IOException {
        return new BackupFile(
                p.getFileName().toString(),
                Files.size(p),
                Files.getLastModifiedTime(p).toInstant(),
                kind);
    }

    private String readPending() {
        Path pending = pendingMarker();
        if (!Files.isRegularFile(pending)) {
            return null;
        }
        try {
            String s = Files.readString(pending).trim();
            return s.isEmpty() ? null : s;
        } catch (IOException e) {
            return null;
        }
    }

    private static String kindOf(String name) {
        if (name.startsWith("pre-restore-")) {
            return "pre-restore";
        }
        if (name.startsWith("billing-")) {
            return "backup";
        }
        if (name.startsWith("uploaded-")) {
            return "upload";
        }
        return null;
    }
}
