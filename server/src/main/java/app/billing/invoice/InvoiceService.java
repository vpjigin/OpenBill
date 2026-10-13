package app.billing.invoice;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.billing.auth.CurrentUser;
import app.billing.common.ApiException;
import app.billing.common.GstStates;
import app.billing.customer.Customer;
import app.billing.customer.CustomerService;
import app.billing.item.Item;
import app.billing.item.ItemRepository;
import app.billing.item.Units;
import app.billing.settings.SettingsService;
import app.billing.user.UserRepository;

@Service
public class InvoiceService {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final InvoiceRepository invoices;
    private final ItemRepository items;
    private final CustomerService customers;
    private final SettingsService settings;
    private final UserRepository users;
    private final JdbcTemplate jdbc;

    public InvoiceService(InvoiceRepository invoices, ItemRepository items, CustomerService customers,
                          SettingsService settings, UserRepository users, JdbcTemplate jdbc) {
        this.invoices = invoices;
        this.items = items;
        this.customers = customers;
        this.settings = settings;
        this.users = users;
        this.jdbc = jdbc;
    }

    public record LineRequest(UUID itemId, long qtyMilli, Long discountPaise) {
        public long discountPaiseOrZero() {
            return discountPaise == null ? 0 : discountPaise;
        }
    }

    public record CustomerInline(String name, String phone, String gstin, String stateCode, String address) {
    }

    public record CreateRequest(List<LineRequest> lines, boolean interState, PaymentMode paymentMode,
                                UUID customerId, CustomerInline customer, Boolean saveCustomer,
                                Long billDiscountPaise) {
        public long billDiscountPaiseOrZero() {
            return billDiscountPaise == null ? 0 : billDiscountPaise;
        }
    }

    private record CustomerSnapshot(UUID customerId, String name, String phone, String gstin, String stateCode,
                                    String address) {
    }

    @Transactional
    public Invoice create(CreateRequest req, CurrentUser by) {
        if (req.lines() == null || req.lines().isEmpty()) {
            throw ApiException.badRequest("Add at least one item");
        }
        if (req.lines().stream().anyMatch(l -> l.itemId() == null || l.qtyMilli() <= 0)) {
            throw ApiException.badRequest("Every line needs an item and a quantity above zero");
        }

        String fy = financialYear(LocalDate.now(ZoneId.systemDefault()));
        long seq = nextNumber(fy);

        List<UUID> ids = req.lines().stream().map(LineRequest::itemId).distinct().toList();
        Map<UUID, Item> byId = items.findAllById(ids).stream()
                .filter(i -> !i.isDeleted() && i.isActive())
                .collect(Collectors.toMap(Item::getId, Function.identity()));
        if (byId.size() != ids.size()) {
            throw ApiException.badRequest("Some items no longer exist. Refresh and try again.");
        }

        for (LineRequest l : req.lines()) {
            Item it = byId.get(l.itemId());
            if (!Units.isDecimal(it.getUnit()) && l.qtyMilli() % 1000 != 0) {
                throw ApiException.badRequest("Quantity for " + it.getName() + " must be a whole number");
            }
        }

        CustomerSnapshot snapshot = resolveCustomer(req);
        String shopState = normalizeState(settings.get().getStateCode());
        String customerState = normalizeState(snapshot.stateCode());
        boolean interState;
        if (shopState != null && customerState != null) {
            interState = !shopState.equals(customerState);
        } else {
            interState = req.interState();
        }

        long billDiscountPaise = req.billDiscountPaiseOrZero();

        List<GstCalculator.LineInput> calcIn = new ArrayList<>();
        for (LineRequest l : req.lines()) {
            Item it = byId.get(l.itemId());
            calcIn.add(new GstCalculator.LineInput(it.getRatePaise(), l.qtyMilli(), it.getGstRateBp(),
                    it.isTaxInclusive(), l.discountPaiseOrZero()));
        }

        GstCalculator.Result calc;
        try {
            calc = GstCalculator.calculate(calcIn, interState, billDiscountPaise);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(e.getMessage());
        }

        String prefix = settings.get().getInvoicePrefix();
        Invoice inv = new Invoice();
        inv.setFy(fy);
        inv.setSeq(seq);
        inv.setInvoiceNo("%s/%s/%06d".formatted(prefix, fy, seq));
        inv.setInvoiceDate(Instant.now());
        inv.setCustomerId(snapshot.customerId());
        inv.setCustomerName(snapshot.name());
        inv.setCustomerPhone(snapshot.phone());
        inv.setCustomerGstin(snapshot.gstin());
        inv.setCustomerStateCode(snapshot.stateCode());
        inv.setCustomerAddress(snapshot.address());
        inv.setInterState(interState);
        inv.setPaymentMode(req.paymentMode() == null ? PaymentMode.CASH : req.paymentMode());
        inv.setGrossPaise(calc.gross());
        inv.setDiscountPaise(calc.discount());
        inv.setBillDiscountPaise(billDiscountPaise);
        inv.setTaxablePaise(calc.taxable());
        inv.setCgstPaise(calc.cgst());
        inv.setSgstPaise(calc.sgst());
        inv.setIgstPaise(calc.igst());
        inv.setRoundOffPaise(calc.roundOff());
        inv.setTotalPaise(calc.total());
        inv.setCreatedBy(by.userId());
        inv.setChannel(by.channel());

        for (int n = 0; n < req.lines().size(); n++) {
            LineRequest l = req.lines().get(n);
            Item it = byId.get(l.itemId());
            GstCalculator.LineResult r = calc.lines().get(n);
            InvoiceLine line = new InvoiceLine();
            line.setLineNo(n + 1);
            line.setItemId(it.getId());
            line.setName(it.getName());
            line.setHsn(it.getHsn());
            line.setQtyMilli(l.qtyMilli());
            line.setUnit(it.getUnit());
            line.setTaxInclusive(it.isTaxInclusive());
            line.setRatePaise(it.getRatePaise());
            line.setGstRateBp(it.getGstRateBp());
            line.setGrossPaise(r.gross());
            line.setDiscountPaise(r.discount());
            line.setTaxablePaise(r.taxable());
            line.setCgstPaise(r.cgst());
            line.setSgstPaise(r.sgst());
            line.setIgstPaise(r.igst());
            line.setTotalPaise(r.total());
            inv.addLine(line);
            it.setStockMilli(it.getStockMilli() - l.qtyMilli());
        }
        return invoices.save(inv);
    }

    private CustomerSnapshot resolveCustomer(CreateRequest req) {
        if (req.customerId() != null) {
            Customer c = customers.get(req.customerId());
            return new CustomerSnapshot(c.getId(), c.getName(), c.getPhone(), c.getGstin(), c.getStateCode(),
                    c.getAddress());
        }
        CustomerInline in = req.customer();
        if (in == null || (blankToNull(in.name()) == null && blankToNull(in.phone()) == null
                && blankToNull(in.gstin()) == null)) {
            return new CustomerSnapshot(null, null, null, null, null, null);
        }
        CustomerService.CustomerData data = new CustomerService.CustomerData(
                in.name() == null ? "" : in.name(),
                in.phone(),
                in.gstin(),
                in.stateCode(),
                in.address(),
                null,
                null);
        if (Boolean.TRUE.equals(req.saveCustomer()) && blankToNull(in.name()) != null) {
            Customer saved = customers.create(data);
            return new CustomerSnapshot(saved.getId(), saved.getName(), saved.getPhone(), saved.getGstin(),
                    saved.getStateCode(), saved.getAddress());
        }
        if (blankToNull(in.name()) == null && blankToNull(in.gstin()) != null) {
            throw ApiException.badRequest("Customer name is required when GSTIN is provided");
        }
        String gstin = blankToNull(in.gstin());
        String state = blankToNull(in.stateCode());
        if (gstin != null) {
            gstin = gstin.toUpperCase();
            if (!gstin.matches(GstStates.GSTIN_PATTERN)) {
                throw ApiException.badRequest("Invalid GSTIN format");
            }
            state = GstStates.stateCodeFromGstin(gstin).orElseThrow(
                    () -> ApiException.badRequest("Invalid GSTIN format"));
        } else if (state != null && !GstStates.isValidCode(state)) {
            throw ApiException.badRequest("Invalid state code");
        }
        return new CustomerSnapshot(null, blankToNull(in.name()), blankToNull(in.phone()), gstin, state,
                blankToNull(in.address()));
    }

    static String normalizeState(String code) {
        if (code == null || code.isBlank()) {
            return null;
        }
        String c = code.trim();
        return GstStates.isValidCode(c) ? c : null;
    }

    @Transactional(readOnly = true)
    public List<Invoice> search(LocalDate from, LocalDate to, String q, int limit) {
        Instant fromInstant = from == null ? null : from.atStartOfDay(ZONE).toInstant();
        Instant toExclusive = to == null ? null : to.plusDays(1).atStartOfDay(ZONE).toInstant();
        String query = q == null || q.isBlank() ? null : q.trim();
        int cap = Math.min(Math.max(limit, 1), 200);
        return invoices.search(fromInstant, toExclusive, query,
                org.springframework.data.domain.PageRequest.of(0, cap));
    }

    @Transactional(readOnly = true)
    public Invoice get(UUID id) {
        Invoice inv = invoices.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("Invoice not found"));
        inv.getLines().size();
        return inv;
    }

    @Transactional(readOnly = true)
    public String createdByName(UUID userId) {
        return users.findById(userId).map(u -> u.getName()).orElse("—");
    }

    /** Indian financial year, April to March: 12 Oct 2026 -> "26-27". */
    public static String financialYear(LocalDate d) {
        int start = d.getMonthValue() >= 4 ? d.getYear() : d.getYear() - 1;
        return "%02d-%02d".formatted(start % 100, (start + 1) % 100);
    }

    private long nextNumber(String fy) {
        jdbc.update("""
                INSERT INTO invoice_sequence (fy, last_number) VALUES (?, 1)
                ON CONFLICT (fy) DO UPDATE SET last_number = last_number + 1""", fy);
        Long n = jdbc.queryForObject("SELECT last_number FROM invoice_sequence WHERE fy = ?", Long.class, fy);
        return n == null ? 1 : n;
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
