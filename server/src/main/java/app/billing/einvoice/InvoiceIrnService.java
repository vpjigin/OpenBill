package app.billing.einvoice;

import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.billing.common.ApiException;
import app.billing.invoice.Invoice;
import app.billing.invoice.InvoiceRepository;

/** Only this service may update IRN / Ack fields on saved invoices. */
@Service
public class InvoiceIrnService {

    private static final Pattern IRN = Pattern.compile("^[0-9a-fA-F]{64}$");

    private final InvoiceRepository invoices;

    public InvoiceIrnService(InvoiceRepository invoices) {
        this.invoices = invoices;
    }

    public record IrnDetails(String irn, String ackNo, String ackDate, String signedQr) {
    }

    @Transactional
    public Invoice recordIrn(UUID invoiceId, IrnDetails details) {
        Invoice inv = invoices.findByIdAndDeletedAtIsNull(invoiceId)
                .orElseThrow(() -> ApiException.notFound("Invoice not found"));
        if (inv.getCustomerGstin() == null || inv.getCustomerGstin().isBlank()) {
            throw ApiException.badRequest("IRN details apply to B2B invoices only");
        }
        if (details.irn() == null || !IRN.matcher(details.irn().trim()).matches()) {
            throw ApiException.badRequest("IRN must be 64 hexadecimal characters");
        }
        if (details.ackNo() == null || details.ackNo().isBlank()) {
            throw ApiException.badRequest("Acknowledgement number is required");
        }
        if (details.ackDate() == null || details.ackDate().isBlank()) {
            throw ApiException.badRequest("Acknowledgement date is required");
        }
        if (details.signedQr() == null || details.signedQr().isBlank()) {
            throw ApiException.badRequest("Signed QR payload is required");
        }
        inv.setIrn(details.irn().trim().toLowerCase(Locale.ROOT));
        inv.setAckNo(details.ackNo().trim());
        inv.setAckDate(details.ackDate().trim());
        inv.setSignedQr(details.signedQr().trim());
        return invoices.save(inv);
    }
}
