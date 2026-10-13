package app.billing.invoice;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import app.billing.auth.CurrentUser;
import app.billing.creditnote.CreditNoteService;

/** Billing website only (ChannelFilter keeps it off the admin port). */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService service;
    private final CreditNoteService creditNotes;

    public InvoiceController(InvoiceService service, CreditNoteService creditNotes) {
        this.service = service;
        this.creditNotes = creditNotes;
    }

    public record CreditNoteBriefDto(UUID id, String cnNo, Instant cnDate, long totalPaise) {
    }

    public record LineDto(UUID id, int lineNo, String name, String hsn, long qtyMilli, String unit,
                          boolean taxInclusive, long ratePaise, int gstRateBp, long grossPaise, long discountPaise,
                          long taxablePaise, long cgstPaise, long sgstPaise, long igstPaise, long totalPaise,
                          Long returnableQtyMilli) {
    }

    public record InvoiceDto(UUID id, String invoiceNo, Instant invoiceDate, UUID customerId, String customerName,
                             String customerPhone, String customerGstin, String customerStateCode,
                             String customerAddress, boolean interState, PaymentMode paymentMode, long grossPaise,
                             long discountPaise, long billDiscountPaise, long taxablePaise, long cgstPaise,
                             long sgstPaise, long igstPaise, long roundOffPaise, long totalPaise,
                             String irn, String ackNo, String ackDate, String signedQr,
                             String createdByName, List<LineDto> lines, List<CreditNoteBriefDto> creditNotes) {
        static InvoiceDto of(Invoice i, boolean withLines, String createdByName,
                             java.util.Map<java.util.UUID, Long> returnableByLine,
                             List<CreditNoteBriefDto> creditNoteList) {
            List<LineDto> lines = withLines ? i.getLines().stream().map(l -> new LineDto(l.getId(), l.getLineNo(),
                    l.getName(), l.getHsn(), l.getQtyMilli(), l.getUnit(), l.isTaxInclusive(), l.getRatePaise(),
                    l.getGstRateBp(), l.getGrossPaise(), l.getDiscountPaise(), l.getTaxablePaise(), l.getCgstPaise(),
                    l.getSgstPaise(), l.getIgstPaise(), l.getTotalPaise(),
                    returnableByLine == null ? null : returnableByLine.getOrDefault(l.getId(), 0L))).toList()
                    : List.of();
            List<CreditNoteBriefDto> cns = creditNoteList == null ? List.of() : creditNoteList;
            return new InvoiceDto(i.getId(), i.getInvoiceNo(), i.getInvoiceDate(), i.getCustomerId(),
                    i.getCustomerName(), i.getCustomerPhone(), i.getCustomerGstin(), i.getCustomerStateCode(),
                    i.getCustomerAddress(), i.isInterState(), i.getPaymentMode(), i.getGrossPaise(),
                    i.getDiscountPaise(), i.getBillDiscountPaise(), i.getTaxablePaise(), i.getCgstPaise(),
                    i.getSgstPaise(), i.getIgstPaise(), i.getRoundOffPaise(), i.getTotalPaise(),
                    i.getIrn(), i.getAckNo(), i.getAckDate(), i.getSignedQr(), createdByName, lines, cns);
        }
    }

    @PostMapping
    public InvoiceDto create(@RequestBody InvoiceService.CreateRequest body) {
        Invoice inv = service.create(body, CurrentUser.get());
        return InvoiceDto.of(inv, true, service.createdByName(inv.getCreatedBy()), null, List.of());
    }

    @GetMapping
    public List<InvoiceDto> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "50") int limit) {
        return service.search(from, to, q, limit).stream()
                .map(i -> InvoiceDto.of(i, false, null, null, List.of()))
                .toList();
    }

    @GetMapping("/{id}")
    public InvoiceDto get(@PathVariable UUID id) {
        Invoice inv = service.get(id);
        var credited = creditNotes.creditedByLine(inv.getId());
        java.util.Map<java.util.UUID, Long> returnable = new java.util.HashMap<>();
        for (var line : inv.getLines()) {
            returnable.put(line.getId(), creditNotes.returnableQtyMilli(line, credited));
        }
        List<CreditNoteBriefDto> cnList = creditNotes.summariesForInvoice(inv.getId()).stream()
                .map(s -> new CreditNoteBriefDto(s.id(), s.cnNo(), s.cnDate(), s.totalPaise()))
                .toList();
        return InvoiceDto.of(inv, true, service.createdByName(inv.getCreatedBy()), returnable, cnList);
    }
}
