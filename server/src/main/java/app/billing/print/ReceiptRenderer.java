package app.billing.print;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

import app.billing.creditnote.RefundMode;
import app.billing.invoice.PaymentMode;
import app.billing.report.ReportService;
import app.billing.settings.ShopSettings;

/** Builds ESC/POS receipts for invoices, credit notes, and day-end summaries. */
public class ReceiptRenderer {

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter DATE_TIME =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm").withZone(ZONE);
    private static final DateTimeFormatter DATE =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy").withZone(ZONE);

    public record ShopHeader(String name, String gstin, String phone, String address) {
        static ShopHeader of(ShopSettings s) {
            return new ShopHeader(s.getShopName(), s.getGstin(), s.getPhone(), s.getAddress());
        }
    }

    public record InvoiceLine(String name, long qtyMilli, String unit, long ratePaise, long totalPaise) {
    }

    public record InvoiceReceipt(String invoiceNo, Instant invoiceDate, String customerName,
                                 PaymentMode paymentMode, boolean interState, long taxablePaise, long cgstPaise,
                                 long sgstPaise, long igstPaise, long roundOffPaise, long totalPaise,
                                 List<InvoiceLine> lines) {
    }

    public record CreditNoteReceipt(String cnNo, Instant cnDate, String invoiceNo, String reason,
                                    RefundMode refundMode, boolean interState, long taxablePaise, long cgstPaise,
                                    long sgstPaise, long igstPaise, long roundOffPaise, long totalPaise,
                                    List<InvoiceLine> lines) {
    }

    public byte[] renderTestPage(Printer printer, ShopHeader shop) {
        int w = printer.effectiveCharsPerLine();
        EscPosBuilder b = new EscPosBuilder(w).init().alignCenter().bold(true).doubleHeight(true)
                .textLine("TEST PRINT").bold(false).doubleHeight(false).alignLeft()
                .textLine(shop.name()).separator('-').textLine("Printer: " + printer.getName())
                .textLine("Paper: " + printer.getPaperMm() + "mm · " + w + " cols")
                .feed(2);
        if (printer.isCut()) {
            b.cut();
        }
        return b.build();
    }

    public byte[] renderInvoice(Printer printer, ShopHeader shop, InvoiceReceipt inv) {
        int w = printer.effectiveCharsPerLine();
        EscPosBuilder b = new EscPosBuilder(w).init().alignCenter().bold(true).textLine(shop.name()).bold(false);
        if (shop.gstin() != null && !shop.gstin().isBlank()) {
            b.textLine("GSTIN " + shop.gstin());
        }
        if (shop.address() != null && !shop.address().isBlank()) {
            b.textLine(shop.address());
        }
        if (shop.phone() != null && !shop.phone().isBlank()) {
            b.textLine("Ph " + shop.phone());
        }
        b.separator('-').alignLeft().bold(true).textLine("TAX INVOICE").bold(false)
                .columns("No", inv.invoiceNo())
                .columns("Date", DATE_TIME.format(inv.invoiceDate()));
        if (inv.customerName() != null && !inv.customerName().isBlank()) {
            b.textLine("Customer: " + inv.customerName());
        }
        b.textLine("Pay: " + inv.paymentMode().name()).separator('-');
        for (InvoiceLine line : inv.lines()) {
            b.textLine(EscPosBuilder.EscPosText.truncate(line.name(), w));
            String qty = EscPosBuilder.EscPosText.qty(line.qtyMilli(), line.unit());
            b.columns(qty, EscPosBuilder.EscPosText.money(line.totalPaise()));
        }
        b.separator('-');
        b.columns("Taxable", EscPosBuilder.EscPosText.money(inv.taxablePaise()));
        if (inv.interState()) {
            b.columns("IGST", EscPosBuilder.EscPosText.money(inv.igstPaise()));
        } else {
            b.columns("CGST", EscPosBuilder.EscPosText.money(inv.cgstPaise()));
            b.columns("SGST", EscPosBuilder.EscPosText.money(inv.sgstPaise()));
        }
        if (inv.roundOffPaise() != 0) {
            b.columns("Round off", EscPosBuilder.EscPosText.money(inv.roundOffPaise()));
        }
        b.bold(true).columns("TOTAL", EscPosBuilder.EscPosText.money(inv.totalPaise())).bold(false)
                .feed(1).alignCenter().textLine("Thank you!").alignLeft().feed(2);
        return finish(b, printer);
    }

    public byte[] renderCreditNote(Printer printer, ShopHeader shop, CreditNoteReceipt cn) {
        int w = printer.effectiveCharsPerLine();
        EscPosBuilder b = new EscPosBuilder(w).init().alignCenter().bold(true).textLine(shop.name()).bold(false)
                .separator('-').alignLeft().bold(true).textLine("CREDIT NOTE").bold(false)
                .columns("No", cn.cnNo())
                .columns("Date", DATE_TIME.format(cn.cnDate()))
                .columns("Invoice", cn.invoiceNo())
                .textLine("Reason: " + cn.reason())
                .textLine("Refund: " + cn.refundMode().name())
                .separator('-');
        for (InvoiceLine line : cn.lines()) {
            b.textLine(EscPosBuilder.EscPosText.truncate(line.name(), w));
            b.columns(EscPosBuilder.EscPosText.qty(line.qtyMilli(), line.unit()),
                    EscPosBuilder.EscPosText.money(line.totalPaise()));
        }
        b.separator('-').bold(true).columns("TOTAL", EscPosBuilder.EscPosText.money(cn.totalPaise())).bold(false)
                .feed(2);
        return finish(b, printer);
    }

    public byte[] renderDayEnd(Printer printer, ShopHeader shop, ReportService.DayEndReport report) {
        int w = printer.effectiveCharsPerLine();
        EscPosBuilder b = new EscPosBuilder(w).init().alignCenter().bold(true).textLine(shop.name()).bold(false)
                .textLine("DAY END").columns("Date", DATE.format(report.date().atStartOfDay(ZONE).toInstant()))
                .separator('-').textLine("Sales").columns("Bills", String.valueOf(report.totals().billsCount()))
                .columns("Total", EscPosBuilder.EscPosText.money(report.totals().totalPaise()));
        for (var mode : report.salesByMode()) {
            b.columns("  " + mode.mode(), EscPosBuilder.EscPosText.money(mode.totalPaise()));
        }
        if (report.purchases().count() > 0) {
            b.separator('-').textLine("Purchases")
                    .columns("Bills", String.valueOf(report.purchases().count()))
                    .columns("Total", EscPosBuilder.EscPosText.money(report.purchases().totalPaise()));
        }
        if (report.firstInvoiceNo() != null) {
            b.textLine("Invoices: " + report.firstInvoiceNo() + " - " + report.lastInvoiceNo());
        }
        b.feed(2);
        return finish(b, printer);
    }

    private byte[] finish(EscPosBuilder b, Printer printer) {
        if (printer.isCut()) {
            b.cut();
        }
        return b.build();
    }

    public byte[] drawerKick() {
        return new EscPosBuilder(32).init().openDrawer().build();
    }
}
