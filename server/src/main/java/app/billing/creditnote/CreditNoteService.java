package app.billing.creditnote;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.billing.activity.ActivityActions;
import app.billing.activity.ActivityService;
import app.billing.auth.CurrentUser;
import app.billing.common.ApiException;
import app.billing.invoice.GstCalculator;
import app.billing.invoice.Invoice;
import app.billing.invoice.InvoiceLine;
import app.billing.invoice.InvoiceRepository;
import app.billing.invoice.InvoiceService;
import app.billing.item.Item;
import app.billing.item.ItemRepository;

@Service
public class CreditNoteService {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final CreditNoteRepository creditNotes;
    private final InvoiceRepository invoices;
    private final ItemRepository items;
    private final JdbcTemplate jdbc;
    private final ActivityService activity;

    public CreditNoteService(CreditNoteRepository creditNotes, InvoiceRepository invoices, ItemRepository items,
                             JdbcTemplate jdbc, ActivityService activity) {
        this.creditNotes = creditNotes;
        this.invoices = invoices;
        this.items = items;
        this.jdbc = jdbc;
        this.activity = activity;
    }

    public record LineRequest(UUID invoiceLineId, long qtyMilli) {
    }

    public record CreateRequest(UUID invoiceId, String reason, RefundMode refundMode, List<LineRequest> lines) {
    }

    public record CreditNoteSummary(UUID id, String cnNo, Instant cnDate, long totalPaise) {
    }

    @Transactional
    public CreditNote create(CreateRequest req, CurrentUser by) {
        if (req.reason() == null || req.reason().isBlank()) {
            throw ApiException.badRequest("Reason is required");
        }
        if (req.refundMode() == null) {
            throw ApiException.badRequest("Refund mode is required");
        }
        if (req.lines() == null || req.lines().isEmpty()) {
            throw ApiException.badRequest("Add at least one line to return");
        }

        Invoice invoice = invoices.findByIdAndDeletedAtIsNull(req.invoiceId())
                .orElseThrow(() -> ApiException.notFound("Invoice not found"));
        invoice.getLines().size();
        Map<UUID, InvoiceLine> lineById = invoice.getLines().stream()
                .collect(Collectors.toMap(InvoiceLine::getId, Function.identity()));

        Map<UUID, CreditedLineTotals> credited = creditedByLine(invoice.getId());

        List<LineRequest> active = req.lines().stream().filter(l -> l.qtyMilli() > 0).toList();
        if (active.isEmpty()) {
            throw ApiException.badRequest("Add at least one line to return");
        }

        for (LineRequest lr : active) {
            InvoiceLine line = lineById.get(lr.invoiceLineId());
            if (line == null) {
                throw ApiException.badRequest("Line does not belong to this invoice");
            }
            CreditedLineTotals prev = credited.getOrDefault(line.getId(), emptyCredited(line.getId()));
            long returnable = line.getQtyMilli() - prev.qtyMilli();
            if (lr.qtyMilli() > returnable) {
                throw ApiException.badRequest("Return quantity exceeds what is still returnable for " + line.getName());
            }
            if (lr.qtyMilli() <= 0) {
                throw ApiException.badRequest("Return quantity must be positive");
            }
        }

        String fy = InvoiceService.financialYear(LocalDate.now(ZONE));
        long seq = nextCnNumber(fy);
        String cnNo = "CN/" + fy + "/" + String.format("%06d", seq);

        CreditNote cn = new CreditNote();
        cn.setCnNo(cnNo);
        cn.setFy(fy);
        cn.setSeq(seq);
        cn.setCnDate(Instant.now());
        cn.setInvoiceId(invoice.getId());
        cn.setInvoiceNo(invoice.getInvoiceNo());
        cn.setCustomerId(invoice.getCustomerId());
        cn.setCustomerName(invoice.getCustomerName());
        cn.setCustomerGstin(invoice.getCustomerGstin());
        cn.setCustomerStateCode(invoice.getCustomerStateCode());
        cn.setInterState(invoice.isInterState());
        cn.setReason(req.reason().trim());
        cn.setRefundMode(req.refundMode());
        cn.setCreatedBy(by.userId());
        cn.setChannel(by.channel());

        int lineNo = 1;
        long sumTaxable = 0;
        long sumCgst = 0;
        long sumSgst = 0;
        long sumIgst = 0;
        long exactTotal = 0;
        Map<UUID, Long> restock = new HashMap<>();

        for (LineRequest lr : active) {
            InvoiceLine line = lineById.get(lr.invoiceLineId());
            CreditedLineTotals prev = credited.getOrDefault(line.getId(), emptyCredited(line.getId()));
            LineAmounts amounts = creditLineAmounts(line, lr.qtyMilli(), prev);

            CreditNoteLine cnl = new CreditNoteLine();
            cnl.setInvoiceLineId(line.getId());
            cnl.setLineNo(lineNo++);
            cnl.setItemId(line.getItemId());
            cnl.setName(line.getName());
            cnl.setHsn(line.getHsn());
            cnl.setUnit(line.getUnit());
            cnl.setQtyMilli(lr.qtyMilli());
            cnl.setRatePaise(line.getRatePaise());
            cnl.setGstRateBp(line.getGstRateBp());
            cnl.setTaxablePaise(amounts.taxablePaise());
            cnl.setCgstPaise(amounts.cgstPaise());
            cnl.setSgstPaise(amounts.sgstPaise());
            cnl.setIgstPaise(amounts.igstPaise());
            cnl.setTotalPaise(amounts.totalPaise());
            cn.addLine(cnl);

            sumTaxable += amounts.taxablePaise();
            sumCgst += amounts.cgstPaise();
            sumSgst += amounts.sgstPaise();
            sumIgst += amounts.igstPaise();
            exactTotal += amounts.totalPaise();

            if (line.getItemId() != null) {
                restock.merge(line.getItemId(), lr.qtyMilli(), Long::sum);
            }
        }

        long roundedTotal = GstCalculator.roundHalfUp(exactTotal, 100) * 100;
        long roundOff = roundedTotal - exactTotal;

        cn.setTaxablePaise(sumTaxable);
        cn.setCgstPaise(sumCgst);
        cn.setSgstPaise(sumSgst);
        cn.setIgstPaise(sumIgst);
        cn.setRoundOffPaise(roundOff);
        cn.setTotalPaise(roundedTotal);

        creditNotes.save(cn);

        for (var e : restock.entrySet()) {
            Item item = items.findById(e.getKey())
                    .orElseThrow(() -> ApiException.badRequest("Item no longer exists"));
            item.setStockMilli(item.getStockMilli() + e.getValue());
            items.save(item);
        }

        activity.log(ActivityActions.CREDIT_NOTE_CREATED, cn.getCnNo(), cn.getInvoiceNo());
        return cn;
    }

    static LineAmounts creditLineAmounts(InvoiceLine line, long returnQty, CreditedLineTotals prev) {
        long origQty = line.getQtyMilli();
        boolean finishes = prev.qtyMilli() + returnQty == origQty;
        if (finishes) {
            long taxable = line.getTaxablePaise() - prev.taxablePaise();
            long cgst = line.getCgstPaise() - prev.cgstPaise();
            long sgst = line.getSgstPaise() - prev.sgstPaise();
            long igst = line.getIgstPaise() - prev.igstPaise();
            long total = line.getTotalPaise() - prev.totalPaise();
            return new LineAmounts(taxable, cgst, sgst, igst, total);
        }
        long taxable = GstCalculator.roundHalfUp(line.getTaxablePaise() * returnQty, origQty);
        long cgst = GstCalculator.roundHalfUp(line.getCgstPaise() * returnQty, origQty);
        long sgst = GstCalculator.roundHalfUp(line.getSgstPaise() * returnQty, origQty);
        long igst = GstCalculator.roundHalfUp(line.getIgstPaise() * returnQty, origQty);
        long total = taxable + cgst + sgst + igst;
        return new LineAmounts(taxable, cgst, sgst, igst, total);
    }

    record LineAmounts(long taxablePaise, long cgstPaise, long sgstPaise, long igstPaise, long totalPaise) {
    }

    @Transactional(readOnly = true)
    public CreditNote get(UUID id) {
        CreditNote cn = creditNotes.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("Credit note not found"));
        cn.getLines().size();
        return cn;
    }

    @Transactional(readOnly = true)
    public List<CreditNote> search(LocalDate from, LocalDate to, int limit) {
        Instant fromInstant = from == null ? null : from.atStartOfDay(ZONE).toInstant();
        Instant toExclusive = to == null ? null : to.plusDays(1).atStartOfDay(ZONE).toInstant();
        int cap = Math.min(Math.max(limit, 1), 200);
        return creditNotes.search(fromInstant, toExclusive,
                org.springframework.data.domain.PageRequest.of(0, cap));
    }

    @Transactional(readOnly = true)
    public List<CreditNoteSummary> summariesForInvoice(UUID invoiceId) {
        return creditNotes.findByInvoiceIdOrderByCnDateAsc(invoiceId).stream()
                .map(cn -> new CreditNoteSummary(cn.getId(), cn.getCnNo(), cn.getCnDate(), cn.getTotalPaise()))
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<UUID, CreditedLineTotals> creditedByLine(UUID invoiceId) {
        List<CreditedLineTotals> rows = jdbc.query("""
                SELECT cnl.invoice_line_id,
                       COALESCE(SUM(cnl.qty_milli), 0),
                       COALESCE(SUM(cnl.taxable_paise), 0),
                       COALESCE(SUM(cnl.cgst_paise), 0),
                       COALESCE(SUM(cnl.sgst_paise), 0),
                       COALESCE(SUM(cnl.igst_paise), 0),
                       COALESCE(SUM(cnl.total_paise), 0)
                FROM credit_note_line cnl
                INNER JOIN credit_note cn ON cn.id = cnl.credit_note_id
                WHERE cn.invoice_id = ?
                  AND cn.deleted_at IS NULL
                  AND cnl.deleted_at IS NULL
                GROUP BY cnl.invoice_line_id
                """, (rs, rowNum) -> new CreditedLineTotals(
                UUID.fromString(rs.getString(1)),
                rs.getLong(2),
                rs.getLong(3),
                rs.getLong(4),
                rs.getLong(5),
                rs.getLong(6),
                rs.getLong(7)), invoiceId.toString());
        Map<UUID, CreditedLineTotals> map = new HashMap<>();
        for (CreditedLineTotals row : rows) {
            map.put(row.invoiceLineId(), row);
        }
        return map;
    }

    @Transactional(readOnly = true)
    public long returnableQtyMilli(InvoiceLine line, Map<UUID, CreditedLineTotals> credited) {
        CreditedLineTotals prev = credited.getOrDefault(line.getId(), emptyCredited(line.getId()));
        return line.getQtyMilli() - prev.qtyMilli();
    }

    private static CreditedLineTotals emptyCredited(UUID lineId) {
        return new CreditedLineTotals(lineId, 0, 0, 0, 0, 0, 0);
    }

    private long nextCnNumber(String fy) {
        String seqKey = "CN/" + fy;
        jdbc.update("""
                INSERT INTO invoice_sequence (fy, last_number) VALUES (?, 1)
                ON CONFLICT (fy) DO UPDATE SET last_number = last_number + 1""", seqKey);
        Long n = jdbc.queryForObject("SELECT last_number FROM invoice_sequence WHERE fy = ?", Long.class, seqKey);
        return n == null ? 1 : n;
    }
}
