package app.billing.purchase;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.billing.auth.CurrentUser;
import app.billing.common.ApiException;
import app.billing.invoice.GstCalculator;
import app.billing.item.Item;
import app.billing.item.ItemRepository;
import app.billing.item.Units;
import app.billing.settings.SettingsService;
import app.billing.supplier.Supplier;
import app.billing.supplier.SupplierService;

@Service
public class PurchaseService {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final PurchaseRepository purchases;
    private final SupplierService suppliers;
    private final ItemRepository items;
    private final SettingsService settings;

    public PurchaseService(PurchaseRepository purchases, SupplierService suppliers, ItemRepository items,
                           SettingsService settings) {
        this.purchases = purchases;
        this.suppliers = suppliers;
        this.items = items;
        this.settings = settings;
    }

    public record LineRequest(UUID itemId, long qtyMilli, long costPaise, int gstRateBp) {
    }

    public record CreateRequest(UUID supplierId, String billNo, LocalDate billDate, String notes,
                                List<LineRequest> lines) {
    }

    @Transactional
    public Purchase create(CreateRequest req, CurrentUser by) {
        if (req.lines() == null || req.lines().isEmpty()) {
            throw ApiException.badRequest("Add at least one line");
        }
        if (req.billNo() == null || req.billNo().isBlank()) {
            throw ApiException.badRequest("Supplier bill number is required");
        }
        if (req.billDate() == null) {
            throw ApiException.badRequest("Bill date is required");
        }
        String billNo = req.billNo().trim();
        Supplier supplier = suppliers.get(req.supplierId());
        if (purchases.existsBySupplierIdAndBillNoAndDeletedAtIsNull(supplier.getId(), billNo)) {
            throw ApiException.conflict("This supplier bill number is already recorded");
        }

        List<UUID> ids = req.lines().stream().map(LineRequest::itemId).distinct().toList();
        Map<UUID, Item> byId = items.findAllById(ids).stream()
                .filter(i -> !i.isDeleted() && i.isActive())
                .collect(Collectors.toMap(Item::getId, Function.identity()));
        if (byId.size() != ids.size()) {
            throw ApiException.badRequest("Some items no longer exist. Refresh and try again.");
        }

        for (LineRequest l : req.lines()) {
            if (l.itemId() == null || l.qtyMilli() <= 0 || l.costPaise() < 0) {
                throw ApiException.badRequest("Every line needs item, quantity, and cost");
            }
            Item it = byId.get(l.itemId());
            if (!Units.isDecimal(it.getUnit()) && l.qtyMilli() % 1000 != 0) {
                throw ApiException.badRequest("Quantity for " + it.getName() + " must be a whole number");
            }
        }

        String shopState = normalizeState(settings.get().getStateCode());
        String supplierState = normalizeState(supplier.getStateCode());
        boolean interState;
        if (shopState != null && supplierState != null) {
            interState = !shopState.equals(supplierState);
        } else {
            interState = false;
        }

        List<GstCalculator.LineInput> calcIn = new ArrayList<>();
        for (LineRequest l : req.lines()) {
            calcIn.add(new GstCalculator.LineInput(l.costPaise(), l.qtyMilli(), l.gstRateBp(), false, 0));
        }

        GstCalculator.Result calc;
        try {
            calc = GstCalculator.calculate(calcIn, interState, 0);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(e.getMessage());
        }

        Purchase p = new Purchase();
        p.setSupplierId(supplier.getId());
        p.setSupplierName(supplier.getName());
        p.setSupplierGstin(supplier.getGstin());
        p.setBillNo(billNo);
        p.setBillDate(req.billDate().atStartOfDay(ZONE).toInstant());
        p.setInterState(interState);
        p.setTaxablePaise(calc.taxable());
        p.setCgstPaise(calc.cgst());
        p.setSgstPaise(calc.sgst());
        p.setIgstPaise(calc.igst());
        p.setRoundOffPaise(calc.roundOff());
        p.setTotalPaise(calc.total());
        p.setNotes(blankToNull(req.notes()));
        p.setCreatedBy(by.userId());

        for (int n = 0; n < req.lines().size(); n++) {
            LineRequest l = req.lines().get(n);
            Item it = byId.get(l.itemId());
            GstCalculator.LineResult r = calc.lines().get(n);
            PurchaseLine line = new PurchaseLine();
            line.setLineNo(n + 1);
            line.setItemId(it.getId());
            line.setName(it.getName());
            line.setHsn(it.getHsn());
            line.setUnit(it.getUnit());
            line.setQtyMilli(l.qtyMilli());
            line.setCostPaise(l.costPaise());
            line.setGstRateBp(l.gstRateBp());
            line.setTaxablePaise(r.taxable());
            line.setCgstPaise(r.cgst());
            line.setSgstPaise(r.sgst());
            line.setIgstPaise(r.igst());
            line.setTotalPaise(r.total());
            p.addLine(line);

            line.setPreviousCostPaise(it.getCostPaise());
            it.setStockMilli(it.getStockMilli() + l.qtyMilli());
            it.setCostPaise(l.costPaise());
        }

        return purchases.save(p);
    }

    @Transactional(readOnly = true)
    public List<Purchase> search(LocalDate from, LocalDate to, UUID supplierId) {
        Instant start = from != null ? from.atStartOfDay(ZONE).toInstant() : Instant.EPOCH;
        Instant endExclusive = to != null ? to.plusDays(1).atStartOfDay(ZONE).toInstant() : Instant.now().plusSeconds(86400);
        return purchases.search(start, endExclusive, supplierId);
    }

    @Transactional(readOnly = true)
    public Purchase get(UUID id) {
        Purchase p = purchases.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("Purchase not found"));
        p.getLines().size();
        return p;
    }

    @Transactional
    public void cancel(UUID id) {
        Purchase p = get(id);
        for (PurchaseLine line : p.getLines()) {
            if (line.isDeleted()) {
                continue;
            }
            Item it = items.findById(line.getItemId()).orElse(null);
            if (it != null && !it.isDeleted()) {
                it.setStockMilli(it.getStockMilli() - line.getQtyMilli());
                if (it.getCostPaise() == line.getCostPaise()) {
                    it.setCostPaise(line.getPreviousCostPaise());
                }
            }
            line.markDeleted();
        }
        p.markDeleted();
        purchases.save(p);
    }

    private static String normalizeState(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        return code.trim();
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
