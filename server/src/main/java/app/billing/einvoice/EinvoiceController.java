package app.billing.einvoice;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import app.billing.invoice.Invoice;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/einvoice")
public class EinvoiceController {

    private final EinvoiceService einvoice;
    private final InvoiceIrnService irnService;

    public EinvoiceController(EinvoiceService einvoice, InvoiceIrnService irnService) {
        this.einvoice = einvoice;
        this.irnService = irnService;
    }

    public record ExportResponse(List<String> problems, List<Map<String, Object>> documents) {
        static ExportResponse of(EinvoiceService.ExportResult r) {
            return new ExportResponse(r.problems(), r.documents());
        }
    }

    public record IrnInput(@NotBlank String irn, @NotBlank String ackNo, @NotBlank String ackDate,
                           @NotBlank String signedQr) {
    }

    public record IrnResponse(UUID id, String irn, String ackNo, String ackDate, String signedQr) {
        static IrnResponse of(Invoice i) {
            return new IrnResponse(i.getId(), i.getIrn(), i.getAckNo(), i.getAckDate(), i.getSignedQr());
        }
    }

    @GetMapping("/json")
    public ExportResponse exportJson(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ExportResponse.of(einvoice.exportJson(from, to));
    }

    @PostMapping("/{invoiceId}/irn")
    public IrnResponse recordIrn(@PathVariable UUID invoiceId, @RequestBody IrnInput body) {
        Invoice updated = irnService.recordIrn(invoiceId,
                new InvoiceIrnService.IrnDetails(body.irn(), body.ackNo(), body.ackDate(), body.signedQr()));
        return IrnResponse.of(updated);
    }
}
