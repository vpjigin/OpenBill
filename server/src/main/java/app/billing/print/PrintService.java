package app.billing.print;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.billing.common.ApiException;
import app.billing.creditnote.CreditNote;
import app.billing.creditnote.CreditNoteService;
import app.billing.invoice.Invoice;
import app.billing.invoice.InvoiceService;
import app.billing.invoice.PaymentMode;
import app.billing.report.ReportService;
import app.billing.settings.SettingsService;

@Service
public class PrintService {

    private final PrinterRepository printers;
    private final RawPrintTransport transport;
    private final ReceiptRenderer renderer;
    private final SettingsService settings;
    private final InvoiceService invoices;
    private final CreditNoteService creditNotes;
    private final ReportService reports;

    public PrintService(PrinterRepository printers, RawPrintTransport transport, SettingsService settings,
                        InvoiceService invoices, CreditNoteService creditNotes, ReportService reports) {
        this.printers = printers;
        this.transport = transport;
        this.renderer = new ReceiptRenderer();
        this.settings = settings;
        this.invoices = invoices;
        this.creditNotes = creditNotes;
        this.reports = reports;
    }

    public record PrintStatus(boolean configured, String defaultPrinterName, int paperMm) {
    }

    @Transactional(readOnly = true)
    public PrintStatus status() {
        return printers.findDefault()
                .map(p -> new PrintStatus(true, p.getName(), p.getPaperMm()))
                .orElse(new PrintStatus(false, null, 80));
    }

    @Transactional(readOnly = true)
    public void printInvoice(UUID invoiceId, UUID printerId) {
        Printer printer = resolvePrinter(printerId);
        Invoice inv = invoices.get(invoiceId);
        var shop = ReceiptRenderer.ShopHeader.of(settings.get());
        List<ReceiptRenderer.InvoiceLine> lines = inv.getLines().stream()
                .map(l -> new ReceiptRenderer.InvoiceLine(l.getName(), l.getQtyMilli(), l.getUnit(), l.getRatePaise(),
                        l.getTotalPaise()))
                .toList();
        var receipt = new ReceiptRenderer.InvoiceReceipt(inv.getInvoiceNo(), inv.getInvoiceDate(), inv.getCustomerName(),
                inv.getPaymentMode(), inv.isInterState(), inv.getTaxablePaise(), inv.getCgstPaise(), inv.getSgstPaise(),
                inv.getIgstPaise(), inv.getRoundOffPaise(), inv.getTotalPaise(), lines);
        byte[] data = renderer.renderInvoice(printer, shop, receipt);
        send(printer, data, inv.getPaymentMode());
    }

    @Transactional(readOnly = true)
    public void printCreditNote(UUID creditNoteId, UUID printerId) {
        Printer printer = resolvePrinter(printerId);
        CreditNote cn = creditNotes.get(creditNoteId);
        var shop = ReceiptRenderer.ShopHeader.of(settings.get());
        List<ReceiptRenderer.InvoiceLine> lines = cn.getLines().stream()
                .map(l -> new ReceiptRenderer.InvoiceLine(l.getName(), l.getQtyMilli(), l.getUnit(), l.getRatePaise(),
                        l.getTotalPaise()))
                .toList();
        var receipt = new ReceiptRenderer.CreditNoteReceipt(cn.getCnNo(), cn.getCnDate(), cn.getInvoiceNo(),
                cn.getReason(), cn.getRefundMode(), cn.isInterState(), cn.getTaxablePaise(), cn.getCgstPaise(),
                cn.getSgstPaise(), cn.getIgstPaise(), cn.getRoundOffPaise(), cn.getTotalPaise(), lines);
        byte[] data = renderer.renderCreditNote(printer, shop, receipt);
        send(printer, data, null);
    }

    @Transactional(readOnly = true)
    public void printDayEnd(LocalDate date, UUID printerId) {
        if (date == null) {
            throw ApiException.badRequest("date is required");
        }
        Printer printer = resolvePrinter(printerId);
        var shop = ReceiptRenderer.ShopHeader.of(settings.get());
        var report = reports.dayEnd(date);
        byte[] data = renderer.renderDayEnd(printer, shop, report);
        send(printer, data, null);
    }

    @Transactional(readOnly = true)
    public void openDrawer(UUID printerId) {
        Printer printer = resolvePrinter(printerId);
        if (!printer.isOpenDrawer()) {
            throw ApiException.conflict("This printer is not configured to open the cash drawer.");
        }
        sendRaw(printer, renderer.drawerKick());
    }

    public void sendRaw(Printer printer, byte[] data) {
        try {
            transport.send(data, printer);
        } catch (PrintTransportException e) {
            throw ApiException.conflict("Printing failed: " + e.getMessage());
        }
    }

    private void send(Printer printer, byte[] data, PaymentMode paymentMode) {
        sendRaw(printer, data);
        if (paymentMode == PaymentMode.CASH && printer.isOpenDrawer()) {
            sendRaw(printer, renderer.drawerKick());
        }
    }

    private Printer resolvePrinter(UUID printerId) {
        Printer p;
        if (printerId != null) {
            p = printers.findById(printerId).filter(pr -> !pr.isDeleted())
                    .orElseThrow(() -> ApiException.notFound("Printer not found"));
        } else {
            p = printers.findDefault()
                    .orElseThrow(() -> ApiException.conflict("No default receipt printer configured. Add one in Admin → Printers."));
        }
        return p;
    }
}
