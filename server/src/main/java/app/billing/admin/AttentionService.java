package app.billing.admin;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.billing.backup.BackupService;
import app.billing.config.AppProperties;
import app.billing.invoice.InvoiceService;
import app.billing.item.ItemRepository;
import app.billing.settings.SettingsService;
import app.billing.settings.ShopSettings;

@Service
public class AttentionService {

    private static final long ONE_GB = 1_000_000_000L;

    private final ItemRepository items;
    private final SettingsService settings;
    private final AppProperties props;
    private final JdbcTemplate jdbc;
    private final BackupService backups;

    public AttentionService(ItemRepository items, SettingsService settings, AppProperties props, JdbcTemplate jdbc,
                            BackupService backups) {
        this.items = items;
        this.settings = settings;
        this.props = props;
        this.jdbc = jdbc;
        this.backups = backups;
    }

    public record AttentionItem(String level, String code, String message) {
    }

    @Transactional(readOnly = true)
    public List<AttentionItem> list() {
        List<AttentionItem> out = new ArrayList<>();
        appendLowStock(out);
        appendBackup(out);
        appendDiskSpace(out);
        appendGstDetails(out);
        appendInvoiceSeries(out);
        return out;
    }

    private void appendLowStock(List<AttentionItem> out) {
        long n = items.countLowStock();
        if (n > 0) {
            out.add(new AttentionItem("warn", "LOW_STOCK",
                    n + (n == 1 ? " item is" : " items are") + " at or below reorder level"));
        }
    }

    private void appendBackup(List<AttentionItem> out) {
        if (backups.isBackupStale()) {
            out.add(new AttentionItem("warn", "BACKUP_STALE",
                    "Last backup is older than 48 hours — back up your data"));
        }
    }

    private void appendDiskSpace(List<AttentionItem> out) {
        try {
            Path data = Path.of(props.dataDir());
            Files.createDirectories(data);
            long free = Files.getFileStore(data).getUsableSpace();
            if (free < ONE_GB) {
                out.add(new AttentionItem("warn", "DISK_SPACE",
                        "Less than 1 GB free on the data drive (" + formatBytes(free) + " left)"));
            }
        } catch (Exception ignored) {
        }
    }

    private void appendGstDetails(List<AttentionItem> out) {
        ShopSettings s = settings.get();
        boolean gstinMissing = s.getGstin() == null || s.getGstin().isBlank();
        boolean stateMissing = s.getStateCode() == null || s.getStateCode().isBlank();
        if (gstinMissing || stateMissing) {
            out.add(new AttentionItem("warn", "GST_DETAILS", "GST details missing in settings"));
        }
    }

    private void appendInvoiceSeries(List<AttentionItem> out) {
        ShopSettings s = settings.get();
        String fy = InvoiceService.financialYear(LocalDate.now(ZoneId.systemDefault()));
        long next = peekNextInvoiceSeq(fy);
        String prefix = s.getInvoicePrefix() == null || s.getInvoicePrefix().isBlank() ? "INV" : s.getInvoicePrefix();
        out.add(new AttentionItem("ok", "INVOICE_SERIES",
                "Next invoice: %s/%s/%06d".formatted(prefix, fy, next)));
    }

    private long peekNextInvoiceSeq(String fy) {
        List<Long> rows = jdbc.query("SELECT last_number FROM invoice_sequence WHERE fy = ?",
                (rs, rowNum) -> rs.getLong(1), fy);
        if (rows.isEmpty()) {
            return 1;
        }
        return rows.getFirst() + 1;
    }

    private static String formatBytes(long bytes) {
        if (bytes >= 1_000_000_000) {
            return "%.1f GB".formatted(bytes / 1_000_000_000.0);
        }
        return "%.0f MB".formatted(bytes / 1_000_000.0);
    }
}
