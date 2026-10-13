package app.billing.report;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReportService {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final JdbcTemplate jdbc;

    public ReportService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record Totals(long billsCount, long grossPaise, long discountPaise, long taxablePaise, long cgstPaise,
                         long sgstPaise, long igstPaise, long roundOffPaise, long totalPaise) {
    }

    public record ModeTotal(String mode, long totalPaise) {
    }

    public record CreditByMode(String mode, long count, long totalPaise) {
    }

    public record ModeNet(String mode, long salesPaise, long refundPaise, long netPaise) {
    }

    public record PurchaseTotals(long count, long taxablePaise, long cgstPaise, long sgstPaise, long igstPaise,
                                 long roundOffPaise, long totalPaise) {
    }

    public record DayEndReport(LocalDate date, Totals totals, PurchaseTotals purchases, List<ModeTotal> salesByMode,
                               List<CreditByMode> creditNotesByMode, List<ModeNet> netByMode, String firstInvoiceNo,
                               String lastInvoiceNo) {
    }

    public record DailySalesRow(LocalDate date, Totals totals, PurchaseTotals purchases) {
    }

    public record HsnRow(String hsn, int gstRateBp, String unit, long qtyMilli, long taxablePaise, long cgstPaise,
                         long sgstPaise, long igstPaise, long totalPaise) {
    }

    @Transactional(readOnly = true)
    public DayEndReport dayEnd(LocalDate date) {
        Range r = dayRange(date);
        Totals totals = invoiceTotals(r.from(), r.toExclusive());
        List<ModeTotal> sales = salesByMode(r.from(), r.toExclusive());
        List<CreditByMode> credits = creditsByMode(r.from(), r.toExclusive());
        List<ModeNet> net = netByMode(sales, credits);
        String first = firstInvoiceNo(r.from(), r.toExclusive());
        String last = lastInvoiceNo(r.from(), r.toExclusive());
        PurchaseTotals purchases = purchaseTotals(r.from(), r.toExclusive());
        return new DayEndReport(date, totals, purchases, sales, credits, net, first, last);
    }

    @Transactional(readOnly = true)
    public List<DailySalesRow> salesRange(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must not be before from");
        }
        List<DailySalesRow> rows = new ArrayList<>();
        for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
            Range r = dayRange(d);
            rows.add(new DailySalesRow(d, invoiceTotals(r.from(), r.toExclusive()),
                    purchaseTotals(r.from(), r.toExclusive())));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<HsnRow> hsnSummary(LocalDate from, LocalDate to) {
        Range r = inclusiveRange(from, to);
        Map<HsnKey, long[]> inv = aggregateInvoiceLines(r.from(), r.toExclusive());
        Map<HsnKey, long[]> cn = aggregateCreditLines(r.from(), r.toExclusive());
        Map<HsnKey, HsnKey> keys = new TreeMap<>();
        inv.keySet().forEach(k -> keys.put(k, k));
        cn.keySet().forEach(k -> keys.put(k, k));
        List<HsnRow> out = new ArrayList<>();
        for (HsnKey key : keys.keySet()) {
            long[] a = inv.getOrDefault(key, zeros());
            long[] b = cn.getOrDefault(key, zeros());
            out.add(new HsnRow(key.hsn(), key.gstRateBp(), key.unit(),
                    a[0] - b[0], a[1] - b[1], a[2] - b[2], a[3] - b[3], a[4] - b[4], a[5] - b[5]));
        }
        return out;
    }

    private record Range(Instant from, Instant toExclusive) {
    }

    private record HsnKey(String hsn, int gstRateBp, String unit) implements Comparable<HsnKey> {
        @Override
        public int compareTo(HsnKey o) {
            int c = hsn.compareTo(o.hsn);
            if (c != 0) {
                return c;
            }
            c = Integer.compare(gstRateBp, o.gstRateBp);
            return c != 0 ? c : unit.compareTo(o.unit);
        }
    }

    private static Range dayRange(LocalDate date) {
        Instant from = date.atStartOfDay(ZONE).toInstant();
        Instant toExclusive = date.plusDays(1).atStartOfDay(ZONE).toInstant();
        return new Range(from, toExclusive);
    }

    private static Range inclusiveRange(LocalDate from, LocalDate to) {
        Instant start = from.atStartOfDay(ZONE).toInstant();
        Instant endExclusive = to.plusDays(1).atStartOfDay(ZONE).toInstant();
        return new Range(start, endExclusive);
    }

    private PurchaseTotals purchaseTotals(Instant from, Instant toExclusive) {
        return jdbc.queryForObject("""
                SELECT COUNT(*),
                       COALESCE(SUM(taxable_paise), 0),
                       COALESCE(SUM(cgst_paise), 0),
                       COALESCE(SUM(sgst_paise), 0),
                       COALESCE(SUM(igst_paise), 0),
                       COALESCE(SUM(round_off_paise), 0),
                       COALESCE(SUM(total_paise), 0)
                FROM purchase
                WHERE deleted_at IS NULL AND bill_date >= ? AND bill_date < ?
                """, (rs, rowNum) -> new PurchaseTotals(
                rs.getLong(1),
                rs.getLong(2),
                rs.getLong(3),
                rs.getLong(4),
                rs.getLong(5),
                rs.getLong(6),
                rs.getLong(7)), from.toEpochMilli(), toExclusive.toEpochMilli());
    }

    private Totals invoiceTotals(Instant from, Instant toExclusive) {
        return jdbc.queryForObject("""
                SELECT COUNT(*),
                       COALESCE(SUM(gross_paise), 0),
                       COALESCE(SUM(discount_paise), 0),
                       COALESCE(SUM(taxable_paise), 0),
                       COALESCE(SUM(cgst_paise), 0),
                       COALESCE(SUM(sgst_paise), 0),
                       COALESCE(SUM(igst_paise), 0),
                       COALESCE(SUM(round_off_paise), 0),
                       COALESCE(SUM(total_paise), 0)
                FROM invoice
                WHERE deleted_at IS NULL AND invoice_date >= ? AND invoice_date < ?
                """, (rs, rowNum) -> new Totals(
                rs.getLong(1),
                rs.getLong(2),
                rs.getLong(3),
                rs.getLong(4),
                rs.getLong(5),
                rs.getLong(6),
                rs.getLong(7),
                rs.getLong(8),
                rs.getLong(9)), from.toEpochMilli(), toExclusive.toEpochMilli());
    }

    private List<ModeTotal> salesByMode(Instant from, Instant toExclusive) {
        return jdbc.query("""
                SELECT payment_mode, COALESCE(SUM(total_paise), 0)
                FROM invoice
                WHERE deleted_at IS NULL AND invoice_date >= ? AND invoice_date < ?
                GROUP BY payment_mode
                ORDER BY payment_mode
                """, (rs, rowNum) -> new ModeTotal(rs.getString(1), rs.getLong(2)),
                from.toEpochMilli(), toExclusive.toEpochMilli());
    }

    private List<CreditByMode> creditsByMode(Instant from, Instant toExclusive) {
        return jdbc.query("""
                SELECT refund_mode, COUNT(*), COALESCE(SUM(total_paise), 0)
                FROM credit_note
                WHERE deleted_at IS NULL AND cn_date >= ? AND cn_date < ?
                GROUP BY refund_mode
                ORDER BY refund_mode
                """, (rs, rowNum) -> new CreditByMode(rs.getString(1), rs.getLong(2), rs.getLong(3)),
                from.toEpochMilli(), toExclusive.toEpochMilli());
    }

    private static List<ModeNet> netByMode(List<ModeTotal> sales, List<CreditByMode> credits) {
        Map<String, long[]> map = new LinkedHashMap<>();
        for (ModeTotal s : sales) {
            map.computeIfAbsent(s.mode(), k -> new long[2])[0] = s.totalPaise();
        }
        for (CreditByMode c : credits) {
            map.computeIfAbsent(c.mode(), k -> new long[2])[1] = c.totalPaise();
        }
        List<ModeNet> out = new ArrayList<>();
        map.entrySet().stream().sorted(Map.Entry.comparingByKey()).forEach(e -> {
            long salesP = e.getValue()[0];
            long refP = e.getValue()[1];
            out.add(new ModeNet(e.getKey(), salesP, refP, salesP - refP));
        });
        return out;
    }

    private String firstInvoiceNo(Instant from, Instant toExclusive) {
        List<String> rows = jdbc.query("""
                SELECT invoice_no FROM invoice
                WHERE deleted_at IS NULL AND invoice_date >= ? AND invoice_date < ?
                ORDER BY invoice_date ASC, seq ASC
                LIMIT 1
                """, (rs, rowNum) -> rs.getString(1), from.toEpochMilli(), toExclusive.toEpochMilli());
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private String lastInvoiceNo(Instant from, Instant toExclusive) {
        List<String> rows = jdbc.query("""
                SELECT invoice_no FROM invoice
                WHERE deleted_at IS NULL AND invoice_date >= ? AND invoice_date < ?
                ORDER BY invoice_date DESC, seq DESC
                LIMIT 1
                """, (rs, rowNum) -> rs.getString(1), from.toEpochMilli(), toExclusive.toEpochMilli());
        return rows.isEmpty() ? null : rows.getFirst();
    }

    private Map<HsnKey, long[]> aggregateInvoiceLines(Instant from, Instant toExclusive) {
        List<Object[]> rows = jdbc.query("""
                SELECT COALESCE(il.hsn, ''), il.gst_rate_bp, il.unit,
                       SUM(il.qty_milli), SUM(il.taxable_paise), SUM(il.cgst_paise),
                       SUM(il.sgst_paise), SUM(il.igst_paise), SUM(il.total_paise)
                FROM invoice_line il
                INNER JOIN invoice i ON i.id = il.invoice_id
                WHERE i.deleted_at IS NULL AND il.deleted_at IS NULL
                  AND i.invoice_date >= ? AND i.invoice_date < ?
                GROUP BY COALESCE(il.hsn, ''), il.gst_rate_bp, il.unit
                """, (rs, rowNum) -> new Object[]{
                rs.getString(1), rs.getInt(2), rs.getString(3),
                rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getLong(7), rs.getLong(8), rs.getLong(9)},
                from.toEpochMilli(), toExclusive.toEpochMilli());
        return toHsnMap(rows);
    }

    private Map<HsnKey, long[]> aggregateCreditLines(Instant from, Instant toExclusive) {
        List<Object[]> rows = jdbc.query("""
                SELECT COALESCE(cnl.hsn, ''), cnl.gst_rate_bp, cnl.unit,
                       SUM(cnl.qty_milli), SUM(cnl.taxable_paise), SUM(cnl.cgst_paise),
                       SUM(cnl.sgst_paise), SUM(cnl.igst_paise), SUM(cnl.total_paise)
                FROM credit_note_line cnl
                INNER JOIN credit_note cn ON cn.id = cnl.credit_note_id
                WHERE cn.deleted_at IS NULL AND cnl.deleted_at IS NULL
                  AND cn.cn_date >= ? AND cn.cn_date < ?
                GROUP BY COALESCE(cnl.hsn, ''), cnl.gst_rate_bp, cnl.unit
                """, (rs, rowNum) -> new Object[]{
                rs.getString(1), rs.getInt(2), rs.getString(3),
                rs.getLong(4), rs.getLong(5), rs.getLong(6), rs.getLong(7), rs.getLong(8), rs.getLong(9)},
                from.toEpochMilli(), toExclusive.toEpochMilli());
        return toHsnMap(rows);
    }

    private static Map<HsnKey, long[]> toHsnMap(List<Object[]> rows) {
        Map<HsnKey, long[]> map = new HashMap<>();
        for (Object[] row : rows) {
            HsnKey key = new HsnKey((String) row[0], (Integer) row[1], (String) row[2]);
            map.put(key, new long[]{(Long) row[3], (Long) row[4], (Long) row[5], (Long) row[6], (Long) row[7], (Long) row[8]});
        }
        return map;
    }

    private static long[] zeros() {
        return new long[6];
    }
}
