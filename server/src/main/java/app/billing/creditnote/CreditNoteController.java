package app.billing.creditnote;

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

@RestController
@RequestMapping("/api/credit-notes")
public class CreditNoteController {

    private final CreditNoteService service;

    public CreditNoteController(CreditNoteService service) {
        this.service = service;
    }

    public record LineDto(int lineNo, UUID invoiceLineId, String name, String hsn, long qtyMilli, String unit,
                          long ratePaise, int gstRateBp, long taxablePaise, long cgstPaise, long sgstPaise,
                          long igstPaise, long totalPaise) {
    }

    public record CreditNoteDto(UUID id, String cnNo, Instant cnDate, UUID invoiceId, String invoiceNo,
                                UUID customerId, String customerName, String customerGstin, String customerStateCode,
                                boolean interState, String reason, RefundMode refundMode, long taxablePaise,
                                long cgstPaise, long sgstPaise, long igstPaise, long roundOffPaise, long totalPaise,
                                List<LineDto> lines) {
        static CreditNoteDto of(CreditNote cn, boolean withLines) {
            List<LineDto> lines = withLines
                    ? cn.getLines().stream()
                            .map(l -> new LineDto(l.getLineNo(), l.getInvoiceLineId(), l.getName(), l.getHsn(),
                                    l.getQtyMilli(), l.getUnit(), l.getRatePaise(), l.getGstRateBp(),
                                    l.getTaxablePaise(), l.getCgstPaise(), l.getSgstPaise(), l.getIgstPaise(),
                                    l.getTotalPaise()))
                            .toList()
                    : List.of();
            return new CreditNoteDto(cn.getId(), cn.getCnNo(), cn.getCnDate(), cn.getInvoiceId(), cn.getInvoiceNo(),
                    cn.getCustomerId(), cn.getCustomerName(), cn.getCustomerGstin(), cn.getCustomerStateCode(),
                    cn.isInterState(), cn.getReason(), cn.getRefundMode(), cn.getTaxablePaise(), cn.getCgstPaise(),
                    cn.getSgstPaise(), cn.getIgstPaise(), cn.getRoundOffPaise(), cn.getTotalPaise(), lines);
        }
    }

    @PostMapping
    public CreditNoteDto create(@RequestBody CreditNoteService.CreateRequest body) {
        CreditNote cn = service.create(body, CurrentUser.get());
        return CreditNoteDto.of(cn, true);
    }

    @GetMapping
    public List<CreditNoteDto> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "50") int limit) {
        return service.search(from, to, limit).stream().map(cn -> CreditNoteDto.of(cn, false)).toList();
    }

    @GetMapping("/{id}")
    public CreditNoteDto get(@PathVariable UUID id) {
        return CreditNoteDto.of(service.get(id), true);
    }
}
