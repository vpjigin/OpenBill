package app.billing.einvoice;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.billing.common.GstStates;
import app.billing.creditnote.CreditNote;
import app.billing.creditnote.CreditNoteRepository;
import app.billing.customer.Customer;
import app.billing.customer.CustomerRepository;
import app.billing.invoice.Invoice;
import app.billing.invoice.InvoiceRepository;
import app.billing.settings.SettingsService;
import app.billing.settings.ShopSettings;

@Service
public class EinvoiceService {

    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final SettingsService settings;
    private final InvoiceRepository invoices;
    private final CreditNoteRepository creditNotes;
    private final CustomerRepository customers;

    public EinvoiceService(SettingsService settings, InvoiceRepository invoices, CreditNoteRepository creditNotes,
                           CustomerRepository customers) {
        this.settings = settings;
        this.invoices = invoices;
        this.creditNotes = creditNotes;
        this.customers = customers;
    }

    public record ExportResult(List<String> problems, List<Map<String, Object>> documents) {
    }

    @Transactional(readOnly = true)
    public ExportResult exportJson(LocalDate from, LocalDate to) {
        if (from == null || to == null) {
            throw new IllegalArgumentException("from and to are required");
        }
        if (to.isBefore(from)) {
            throw new IllegalArgumentException("to must be on or after from");
        }
        Instant fromInstant = from.atStartOfDay(ZONE).toInstant();
        Instant toExclusive = to.plusDays(1).atStartOfDay(ZONE).toInstant();

        ShopSettings shop = settings.get();
        List<String> problems = new ArrayList<>(EinvoiceValidator.validateShop(shop));
        if (!problems.isEmpty()) {
            return new ExportResult(problems, List.of());
        }

        List<Invoice> invList = invoices.findB2bWithoutIrn(fromInstant, toExclusive, PageRequest.of(0, 5000));
        List<CreditNote> cnList = creditNotes.findB2bInRange(fromInstant, toExclusive, PageRequest.of(0, 5000));

        Map<UUID, Customer> customerById = loadCustomers(invList, cnList);
        Map<UUID, Invoice> invoiceById = loadInvoicesForCreditNotes(cnList);

        for (Invoice inv : invList) {
            EinvoiceJsonBuilder.EinvoiceParty buyer = buyerForInvoice(inv, customerById, shop.getStateCode());
            problems.addAll(EinvoiceValidator.validateInvoice(inv, buyer));
        }
        for (CreditNote cn : cnList) {
            Invoice original = invoiceById.get(cn.getInvoiceId());
            if (original == null) {
                problems.add(cn.getCnNo() + ": original invoice not found");
                continue;
            }
            EinvoiceJsonBuilder.EinvoiceParty buyer = buyerForCreditNote(cn, original, customerById,
                    shop.getStateCode());
            problems.addAll(EinvoiceValidator.validateCreditNote(cn, buyer));
        }
        if (!problems.isEmpty()) {
            return new ExportResult(problems.stream().distinct().toList(), List.of());
        }

        List<Map<String, Object>> documents = new ArrayList<>();
        for (Invoice inv : invList) {
            EinvoiceJsonBuilder.EinvoiceParty buyer = buyerForInvoice(inv, customerById, shop.getStateCode());
            documents.add(EinvoiceJsonBuilder.invoiceDocument(inv, shop, buyer));
        }
        for (CreditNote cn : cnList) {
            Invoice original = invoiceById.get(cn.getInvoiceId());
            EinvoiceJsonBuilder.EinvoiceParty buyer = buyerForCreditNote(cn, original, customerById,
                    shop.getStateCode());
            documents.add(EinvoiceJsonBuilder.creditNoteDocument(cn, original, shop, buyer));
        }
        return new ExportResult(List.of(), documents);
    }

    private Map<UUID, Customer> loadCustomers(List<Invoice> invList, List<CreditNote> cnList) {
        List<UUID> ids = new ArrayList<>();
        for (Invoice i : invList) {
            if (i.getCustomerId() != null) {
                ids.add(i.getCustomerId());
            }
        }
        for (CreditNote cn : cnList) {
            if (cn.getCustomerId() != null) {
                ids.add(cn.getCustomerId());
            }
        }
        if (ids.isEmpty()) {
            return Map.of();
        }
        return customers.findAllById(ids.stream().distinct().toList()).stream()
                .filter(c -> c.getDeletedAt() == null)
                .collect(Collectors.toMap(Customer::getId, Function.identity()));
    }

    private Map<UUID, Invoice> loadInvoicesForCreditNotes(List<CreditNote> cnList) {
        List<UUID> ids = cnList.stream().map(CreditNote::getInvoiceId).distinct().toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return invoices.findAllById(ids).stream()
                .filter(i -> i.getDeletedAt() == null)
                .collect(Collectors.toMap(Invoice::getId, Function.identity()));
    }

    private static EinvoiceJsonBuilder.EinvoiceParty buyerForInvoice(Invoice inv, Map<UUID, Customer> customers,
                                                                     String shopState) {
        Customer c = inv.getCustomerId() == null ? null : customers.get(inv.getCustomerId());
        String gstin = normalizeGstin(inv.getCustomerGstin());
        String state = resolveState(gstin, inv.getCustomerStateCode(), shopState);
        String pos = EinvoiceFormat.resolvePosCode(gstin, inv.getCustomerStateCode(), inv.isInterState(), shopState);
        String pin = c != null ? c.getPincode() : null;
        String address = inv.getCustomerAddress() != null ? inv.getCustomerAddress()
                : (c != null ? c.getAddress() : null);
        String phone = inv.getCustomerPhone() != null ? inv.getCustomerPhone() : (c != null ? c.getPhone() : null);
        String name = inv.getCustomerName() != null ? inv.getCustomerName() : (c != null ? c.getName() : null);
        return new EinvoiceJsonBuilder.EinvoiceParty(gstin, name, address, state, pos, pin, phone);
    }

    private static EinvoiceJsonBuilder.EinvoiceParty buyerForCreditNote(CreditNote cn, Invoice original,
                                                                        Map<UUID, Customer> customers,
                                                                        String shopState) {
        Customer c = cn.getCustomerId() == null ? null : customers.get(cn.getCustomerId());
        String gstin = normalizeGstin(cn.getCustomerGstin());
        String state = resolveState(gstin, cn.getCustomerStateCode(), shopState);
        String pos = EinvoiceFormat.resolvePosCode(gstin, cn.getCustomerStateCode(), cn.isInterState(), shopState);
        String pin = c != null ? c.getPincode() : null;
        String address = original.getCustomerAddress() != null ? original.getCustomerAddress()
                : (c != null ? c.getAddress() : null);
        String phone = original.getCustomerPhone() != null ? original.getCustomerPhone()
                : (c != null ? c.getPhone() : null);
        String name = cn.getCustomerName() != null ? cn.getCustomerName() : (c != null ? c.getName() : null);
        return new EinvoiceJsonBuilder.EinvoiceParty(gstin, name, address, state, pos, pin, phone);
    }

    private static String normalizeGstin(String gstin) {
        return gstin == null ? null : gstin.trim().toUpperCase(java.util.Locale.ROOT);
    }

    private static String resolveState(String gstin, String customerState, String shopState) {
        if (gstin != null && !gstin.isBlank()) {
            return GstStates.stateCodeFromGstin(gstin).orElse(customerState != null ? customerState : shopState);
        }
        return customerState != null ? customerState : shopState;
    }
}
