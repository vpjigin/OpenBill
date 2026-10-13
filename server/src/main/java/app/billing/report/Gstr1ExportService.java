package app.billing.report;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.billing.settings.SettingsService;
import app.billing.settings.ShopSettings;

@Service
public class Gstr1ExportService {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final JdbcTemplate jdbc;
    private final SettingsService settings;
    private final ReportService reports;

    public Gstr1ExportService(JdbcTemplate jdbc, SettingsService settings, ReportService reports) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.reports = reports;
    }

    public record Gstr1Zip(byte[] bytes, String filename) {
    }

    @Transactional(readOnly = true)
    public Gstr1Zip export(YearMonth month) {
        if (month == null) {
            throw new IllegalArgumentException("month is required");
        }
        ShopSettings shop = settings.get();
        String shopState = shop.getStateCode() != null ? shop.getStateCode() : "32";
        Instant from = month.atDay(1).atStartOfDay(ZONE).toInstant();
        Instant toExclusive = month.plusMonths(1).atDay(1).atStartOfDay(ZONE).toInstant();
        long fromMs = from.toEpochMilli();
        long toMs = toExclusive.toEpochMilli();

        byte[] b2b = Gstr1Csv.toUtf8(Gstr1Csv.rows(b2bHeader(), buildB2b(fromMs, toMs, shopState)));
        byte[] b2cl = Gstr1Csv.toUtf8(Gstr1Csv.rows(b2clHeader(), buildB2cl(fromMs, toMs, shopState)));
        byte[] b2cs = Gstr1Csv.toUtf8(Gstr1Csv.rows(b2csHeader(), buildB2cs(fromMs, toMs, shopState)));
        byte[] cdnr = Gstr1Csv.toUtf8(Gstr1Csv.rows(cdnrHeader(), buildCdnr(fromMs, toMs, shopState)));
        byte[] hsn = Gstr1Csv.toUtf8(Gstr1Csv.rows(hsnHeader(), buildHsn(month)));
        byte[] docs = Gstr1Csv.toUtf8(Gstr1Csv.rows(docsHeader(), buildDocs(fromMs, toMs)));

        String slug = Gstr1Format.filenameSlug(shop.getGstin(), shop.getShopName());
        String filename = "GSTR1-" + slug + "-" + month + ".zip";
        byte[] zip = zipEntries(Map.of(
                "b2b.csv", b2b,
                "b2cl.csv", b2cl,
                "b2cs.csv", b2cs,
                "cdnr.csv", cdnr,
                "hsn.csv", hsn,
                "docs.csv", docs));
        return new Gstr1Zip(zip, filename);
    }

    private static byte[] zipEntries(Map<String, byte[]> files) {
        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zip = new ZipOutputStream(baos, StandardCharsets.UTF_8)) {
                for (Map.Entry<String, byte[]> e : files.entrySet()) {
                    ZipEntry entry = new ZipEntry(e.getKey());
                    zip.putNextEntry(entry);
                    zip.write(e.getValue());
                    zip.closeEntry();
                }
            }
            return baos.toByteArray();
        } catch (Exception ex) {
            throw new IllegalStateException("Failed to build GSTR-1 zip", ex);
        }
    }

    private List<String[]> buildB2b(long fromMs, long toMs, String shopState) {
        return jdbc.query("""
                SELECT i.customer_gstin, i.customer_name, i.invoice_no, i.invoice_date, i.total_paise,
                       i.customer_state_code, i.inter_state, il.gst_rate_bp, SUM(il.taxable_paise) AS taxable
                FROM invoice i
                INNER JOIN invoice_line il ON il.invoice_id = i.id AND il.deleted_at IS NULL
                WHERE i.deleted_at IS NULL
                  AND i.invoice_date >= ? AND i.invoice_date < ?
                  AND i.customer_gstin IS NOT NULL AND TRIM(i.customer_gstin) <> ''
                GROUP BY i.id, il.gst_rate_bp
                ORDER BY i.invoice_date, i.seq, il.gst_rate_bp
                """, (rs, rowNum) -> {
            String gstin = rs.getString(1);
            boolean inter = rs.getInt(7) == 1;
            String pos = Gstr1Format.placeOfSupply(
                    Gstr1Format.resolvePosCode(gstin, rs.getString(6), inter, shopState));
            Instant invDate = Instant.ofEpochMilli(rs.getLong(4));
            int rateBp = rs.getInt(8);
            return new String[]{
                    gstin,
                    nullToEmpty(rs.getString(2)),
                    rs.getString(3),
                    Gstr1Format.formatDate(invDate),
                    Gstr1Format.rupees(rs.getLong(5)),
                    pos,
                    "N",
                    "",
                    "Regular B2B",
                    "",
                    Gstr1Format.ratePercent(rateBp),
                    Gstr1Format.rupees(rs.getLong(9)),
                    "0"
            };
        }, fromMs, toMs);
    }

    private List<String[]> buildB2cl(long fromMs, long toMs, String shopState) {
        return jdbc.query("""
                SELECT i.invoice_no, i.invoice_date, i.total_paise, i.customer_state_code, i.inter_state,
                       il.gst_rate_bp, SUM(il.taxable_paise) AS taxable
                FROM invoice i
                INNER JOIN invoice_line il ON il.invoice_id = i.id AND il.deleted_at IS NULL
                WHERE i.deleted_at IS NULL
                  AND i.invoice_date >= ? AND i.invoice_date < ?
                  AND (i.customer_gstin IS NULL OR TRIM(i.customer_gstin) = '')
                  AND i.inter_state = 1
                  AND i.total_paise > ?
                GROUP BY i.id, il.gst_rate_bp
                ORDER BY i.invoice_date, i.seq, il.gst_rate_bp
                """, (rs, rowNum) -> {
            String pos = Gstr1Format.placeOfSupply(
                    Gstr1Format.resolvePosCode(null, rs.getString(4), true, shopState));
            Instant invDate = Instant.ofEpochMilli(rs.getLong(2));
            int rateBp = rs.getInt(6);
            return new String[]{
                    rs.getString(1),
                    Gstr1Format.formatDate(invDate),
                    Gstr1Format.rupees(rs.getLong(3)),
                    pos,
                    "",
                    Gstr1Format.ratePercent(rateBp),
                    Gstr1Format.rupees(rs.getLong(7)),
                    "0",
                    ""
            };
        }, fromMs, toMs, Gstr1Format.B2CL_LIMIT_PAISE);
    }

    private List<String[]> buildB2cs(long fromMs, long toMs, String shopState) {
        Map<B2csKey, Long> taxable = new TreeMap<>();
        jdbc.query("""
                SELECT i.customer_gstin, i.customer_state_code, i.inter_state, i.total_paise,
                       il.gst_rate_bp, SUM(il.taxable_paise) AS taxable
                FROM invoice i
                INNER JOIN invoice_line il ON il.invoice_id = i.id AND il.deleted_at IS NULL
                WHERE i.deleted_at IS NULL
                  AND i.invoice_date >= ? AND i.invoice_date < ?
                  AND (i.customer_gstin IS NULL OR TRIM(i.customer_gstin) = '')
                GROUP BY i.id, il.gst_rate_bp
                """, rs -> {
            String gstin = rs.getString(1);
            boolean inter = rs.getInt(3) == 1;
            long total = rs.getLong(4);
            if (Gstr1Format.isB2cl(inter, total, gstin)) {
                return;
            }
            String posCode = Gstr1Format.resolvePosCode(gstin, rs.getString(2), inter, shopState);
            int rateBp = rs.getInt(5);
            long tax = rs.getLong(6);
            B2csKey key = new B2csKey(Gstr1Format.placeOfSupply(posCode), rateBp);
            taxable.merge(key, tax, Long::sum);
        }, fromMs, toMs);

        jdbc.query("""
                SELECT i.customer_gstin, i.customer_state_code, i.inter_state, i.total_paise,
                       cnl.gst_rate_bp, SUM(cnl.taxable_paise) AS taxable
                FROM credit_note cn
                INNER JOIN credit_note_line cnl ON cnl.credit_note_id = cn.id AND cnl.deleted_at IS NULL
                INNER JOIN invoice i ON i.id = cn.invoice_id
                WHERE cn.deleted_at IS NULL
                  AND cn.cn_date >= ? AND cn.cn_date < ?
                  AND (i.customer_gstin IS NULL OR TRIM(i.customer_gstin) = '')
                GROUP BY cn.id, cnl.gst_rate_bp, i.customer_gstin, i.customer_state_code, i.inter_state, i.total_paise
                """, rs -> {
            String gstin = rs.getString(1);
            boolean inter = rs.getInt(3) == 1;
            long total = rs.getLong(4);
            if (Gstr1Format.isB2cl(inter, total, gstin)) {
                return;
            }
            String posCode = Gstr1Format.resolvePosCode(gstin, rs.getString(2), inter, shopState);
            int rateBp = rs.getInt(5);
            long tax = rs.getLong(6);
            B2csKey key = new B2csKey(Gstr1Format.placeOfSupply(posCode), rateBp);
            taxable.merge(key, -tax, Long::sum);
        }, fromMs, toMs);

        List<String[]> rows = new ArrayList<>();
        for (Map.Entry<B2csKey, Long> e : taxable.entrySet()) {
            if (e.getValue() == 0) {
                continue;
            }
            B2csKey k = e.getKey();
            rows.add(new String[]{
                    "OE",
                    k.pos(),
                    "",
                    Gstr1Format.ratePercent(k.rateBp()),
                    Gstr1Format.rupees(e.getValue()),
                    "0",
                    ""
            });
        }
        return rows;
    }

    private List<String[]> buildCdnr(long fromMs, long toMs, String shopState) {
        return jdbc.query("""
                SELECT cn.customer_gstin, cn.customer_name, cn.cn_no, cn.cn_date, cn.total_paise,
                       cn.customer_state_code, cn.inter_state, cnl.gst_rate_bp, SUM(cnl.taxable_paise) AS taxable
                FROM credit_note cn
                INNER JOIN credit_note_line cnl ON cnl.credit_note_id = cn.id AND cnl.deleted_at IS NULL
                WHERE cn.deleted_at IS NULL
                  AND cn.cn_date >= ? AND cn.cn_date < ?
                  AND cn.customer_gstin IS NOT NULL AND TRIM(cn.customer_gstin) <> ''
                GROUP BY cn.id, cnl.gst_rate_bp
                ORDER BY cn.cn_date, cn.seq, cnl.gst_rate_bp
                """, (rs, rowNum) -> {
            String gstin = rs.getString(1);
            boolean inter = rs.getInt(7) == 1;
            String pos = Gstr1Format.placeOfSupply(
                    Gstr1Format.resolvePosCode(gstin, rs.getString(6), inter, shopState));
            Instant cnDate = Instant.ofEpochMilli(rs.getLong(4));
            int rateBp = rs.getInt(8);
            return new String[]{
                    gstin,
                    nullToEmpty(rs.getString(2)),
                    rs.getString(3),
                    Gstr1Format.formatDate(cnDate),
                    "C",
                    pos,
                    "N",
                    "Regular",
                    Gstr1Format.rupees(rs.getLong(5)),
                    "",
                    Gstr1Format.ratePercent(rateBp),
                    Gstr1Format.rupees(rs.getLong(9)),
                    "0"
            };
        }, fromMs, toMs);
    }

    private List<String[]> buildHsn(YearMonth month) {
        var rows = reports.hsnSummary(month.atDay(1), month.atEndOfMonth());
        List<String[]> out = new ArrayList<>();
        for (ReportService.HsnRow r : rows) {
            if (r.qtyMilli() == 0 && r.taxablePaise() == 0 && r.totalPaise() == 0) {
                continue;
            }
            out.add(new String[]{
                    nullToEmpty(r.hsn()),
                    "",
                    Gstr1Format.uqc(r.unit()),
                    Gstr1Format.quantity(r.qtyMilli()),
                    Gstr1Format.rupees(r.totalPaise()),
                    Gstr1Format.ratePercent(r.gstRateBp()),
                    Gstr1Format.rupees(r.taxablePaise()),
                    Gstr1Format.rupees(r.igstPaise()),
                    Gstr1Format.rupees(r.cgstPaise()),
                    Gstr1Format.rupees(r.sgstPaise()),
                    "0"
            });
        }
        return out;
    }

    private List<String[]> buildDocs(long fromMs, long toMs) {
        List<String[]> rows = new ArrayList<>();
        jdbc.query("""
                SELECT MIN(invoice_no), MAX(invoice_no), COUNT(*)
                FROM invoice
                WHERE deleted_at IS NULL AND invoice_date >= ? AND invoice_date < ?
                """, rs -> {
            long count = rs.getLong(3);
            if (count > 0) {
                rows.add(new String[]{
                        "Invoices for outward supply",
                        rs.getString(1),
                        rs.getString(2),
                        String.valueOf(count),
                        "0"
                });
            }
        }, fromMs, toMs);

        jdbc.query("""
                SELECT MIN(cn_no), MAX(cn_no), COUNT(*)
                FROM credit_note
                WHERE deleted_at IS NULL AND cn_date >= ? AND cn_date < ?
                """, rs -> {
            long count = rs.getLong(3);
            if (count > 0) {
                rows.add(new String[]{
                        "Credit Note",
                        rs.getString(1),
                        rs.getString(2),
                        String.valueOf(count),
                        "0"
                });
            }
        }, fromMs, toMs);
        return rows;
    }

    private record B2csKey(String pos, int rateBp) implements Comparable<B2csKey> {
        @Override
        public int compareTo(B2csKey o) {
            int c = pos.compareTo(o.pos);
            return c != 0 ? c : Integer.compare(rateBp, o.rateBp);
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }

    private static String[] b2bHeader() {
        return new String[]{
                "GSTIN/UIN of Recipient", "Receiver Name", "Invoice Number", "Invoice date", "Invoice Value",
                "Place Of Supply", "Reverse Charge", "Applicable % of Tax Rate", "Invoice Type", "E-Commerce GSTIN",
                "Rate", "Taxable Value", "Cess Amount"
        };
    }

    private static String[] b2clHeader() {
        return new String[]{
                "Invoice Number", "Invoice date", "Invoice Value", "Place Of Supply", "Applicable % of Tax Rate",
                "Rate", "Taxable Value", "Cess Amount", "E-Commerce GSTIN"
        };
    }

    private static String[] b2csHeader() {
        return new String[]{
                "Type", "Place Of Supply", "Applicable % of Tax Rate", "Rate", "Taxable Value", "Cess Amount",
                "E-Commerce GSTIN"
        };
    }

    private static String[] cdnrHeader() {
        return new String[]{
                "GSTIN/UIN of Recipient", "Receiver Name", "Note Number", "Note Date", "Note Type",
                "Place Of Supply", "Reverse Charge", "Note Supply Type", "Note Value", "Applicable % of Tax Rate",
                "Rate", "Taxable Value", "Cess Amount"
        };
    }

    private static String[] hsnHeader() {
        return new String[]{
                "HSN", "Description", "UQC", "Total Quantity", "Total Value", "Rate", "Taxable Value",
                "Integrated Tax Amount", "Central Tax Amount", "State/UT Tax Amount", "Cess Amount"
        };
    }

    private static String[] docsHeader() {
        return new String[]{
                "Nature of Document", "Sr. No. From", "Sr. No. To", "Total Number", "Cancelled"
        };
    }
}
