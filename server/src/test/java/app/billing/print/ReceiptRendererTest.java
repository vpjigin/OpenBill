package app.billing.print;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import app.billing.creditnote.RefundMode;
import app.billing.invoice.PaymentMode;
import app.billing.report.ReportService;
import app.billing.report.ReportService.CreditByMode;
import app.billing.report.ReportService.DayEndReport;
import app.billing.report.ReportService.ModeNet;
import app.billing.report.ReportService.ModeTotal;
import app.billing.report.ReportService.PurchaseTotals;
import app.billing.report.ReportService.Totals;

class ReceiptRendererTest {

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(Math.min(bytes.length * 2, 400));
        int limit = Math.min(bytes.length, 80);
        for (int i = 0; i < limit; i++) {
            sb.append(String.format("%02x", bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    @Test
    void invoiceReceiptStartsWithInitAndShopName() {
        Printer p = new Printer();
        p.setPaperMm(80);
        p.setCut(false);
        var shop = new ReceiptRenderer.ShopHeader("Test Shop", "29AAAAA0000A1Z5", null, null);
        var inv = new ReceiptRenderer.InvoiceReceipt("INV-1", Instant.parse("2026-01-15T10:30:00Z"), "Walk-in",
                PaymentMode.CASH, false, 10_000, 900, 900, 0, 0, 11_800,
                List.of(new ReceiptRenderer.InvoiceLine("Soap", 1000, "NOS", 10_000, 11_800)));
        byte[] data = new ReceiptRenderer().renderInvoice(p, shop, inv);
        assertThat(hex(data)).startsWith("1b401b6101"); // init + center
        assertThat(new String(data)).contains("Test Shop").contains("TAX INVOICE").contains("INV-1");
    }

    @Test
    void dayEndReportContainsSalesHeading() {
        Printer p = new Printer();
        p.setPaperMm(58);
        p.setCut(true);
        var shop = new ReceiptRenderer.ShopHeader("Shop", null, null, null);
        Totals totals = new Totals(3, 50_000, 0, 45_000, 2_000, 2_000, 0, 0, 49_000);
        PurchaseTotals purchases = new PurchaseTotals(0, 0, 0, 0, 0, 0, 0);
        DayEndReport report = new DayEndReport(LocalDate.of(2026, 1, 15), totals, purchases,
                List.of(new ModeTotal("CASH", 49_000)), List.<CreditByMode>of(), List.<ModeNet>of(), "INV-1", "INV-3");
        byte[] data = new ReceiptRenderer().renderDayEnd(p, shop, report);
        assertThat(new String(data)).contains("DAY END").contains("Sales");
        assertThat(data[data.length - 3]).isEqualTo((byte) 0x1D);
        assertThat(data[data.length - 2]).isEqualTo((byte) 0x56);
        assertThat(data[data.length - 1]).isEqualTo((byte) 0x00);
    }
}
