package app.billing.purchase;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import app.billing.auth.CurrentUser;
import app.billing.user.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/purchases")
public class PurchaseController {

    private final PurchaseService service;
    private final UserRepository users;

    public PurchaseController(PurchaseService service, UserRepository users) {
        this.service = service;
        this.users = users;
    }

    public record LineDto(int lineNo, UUID itemId, String name, String hsn, String unit, long qtyMilli,
                          long costPaise, int gstRateBp, long taxablePaise, long cgstPaise, long sgstPaise,
                          long igstPaise, long totalPaise) {
        static LineDto of(PurchaseLine l) {
            return new LineDto(l.getLineNo(), l.getItemId(), l.getName(), l.getHsn(), l.getUnit(), l.getQtyMilli(),
                    l.getCostPaise(), l.getGstRateBp(), l.getTaxablePaise(), l.getCgstPaise(), l.getSgstPaise(),
                    l.getIgstPaise(), l.getTotalPaise());
        }
    }

    private static final ZoneId ZONE = ZoneId.systemDefault();

    public record PurchaseSummaryDto(UUID id, UUID supplierId, String supplierName, String billNo, LocalDate billDate,
                                     long totalPaise) {
        static PurchaseSummaryDto of(Purchase p) {
            return new PurchaseSummaryDto(p.getId(), p.getSupplierId(), p.getSupplierName(), p.getBillNo(),
                    p.getBillDate().atZone(ZONE).toLocalDate(), p.getTotalPaise());
        }
    }

    public record PurchaseDto(UUID id, UUID supplierId, String supplierName, String supplierGstin, String billNo,
                              LocalDate billDate, boolean interState, long taxablePaise, long cgstPaise, long sgstPaise,
                              long igstPaise, long roundOffPaise, long totalPaise, String notes, String createdByName,
                              List<LineDto> lines, boolean cancelled) {
    }

    public record LineInput(@NotNull UUID itemId, long qtyMilli, long costPaise, int gstRateBp) {
        PurchaseService.LineRequest toRequest() {
            return new PurchaseService.LineRequest(itemId, qtyMilli, costPaise, gstRateBp);
        }
    }

    public record CreateInput(@NotNull UUID supplierId, @NotBlank String billNo,
                              @NotNull @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate billDate,
                              String notes, @NotNull List<LineInput> lines) {
        PurchaseService.CreateRequest toRequest() {
            return new PurchaseService.CreateRequest(supplierId, billNo, billDate, notes,
                    lines.stream().map(LineInput::toRequest).toList());
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<PurchaseSummaryDto> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) UUID supplierId) {
        LocalDate f = from != null ? from : LocalDate.now().minusMonths(1);
        LocalDate t = to != null ? to : LocalDate.now();
        return service.search(f, t, supplierId).stream().map(PurchaseSummaryDto::of).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public PurchaseDto get(@PathVariable UUID id) {
        return toDto(service.get(id));
    }

    @PostMapping
    @Transactional
    public PurchaseDto create(@Valid @RequestBody CreateInput in, @AuthenticationPrincipal CurrentUser user) {
        return toDto(service.create(in.toRequest(), user));
    }

    @PostMapping("/{id}/cancel")
    @Transactional
    public void cancel(@PathVariable UUID id) {
        service.cancel(id);
    }

    private PurchaseDto toDto(Purchase p) {
        String byName = users.findById(p.getCreatedBy()).map(u -> u.getName()).orElse(null);
        return new PurchaseDto(
                p.getId(),
                p.getSupplierId(),
                p.getSupplierName(),
                p.getSupplierGstin(),
                p.getBillNo(),
                p.getBillDate().atZone(ZONE).toLocalDate(),
                p.isInterState(),
                p.getTaxablePaise(),
                p.getCgstPaise(),
                p.getSgstPaise(),
                p.getIgstPaise(),
                p.getRoundOffPaise(),
                p.getTotalPaise(),
                p.getNotes(),
                byName,
                p.getLines().stream().filter(l -> !l.isDeleted()).map(LineDto::of).toList(),
                p.isDeleted());
    }
}
