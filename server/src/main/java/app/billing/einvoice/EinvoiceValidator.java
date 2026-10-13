package app.billing.einvoice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import app.billing.common.GstStates;
import app.billing.creditnote.CreditNote;
import app.billing.creditnote.CreditNoteLine;
import app.billing.invoice.Invoice;
import app.billing.invoice.InvoiceLine;
import app.billing.settings.ShopSettings;

final class EinvoiceValidator {

    private EinvoiceValidator() {
    }

    static List<String> validateShop(ShopSettings shop) {
        List<String> problems = new ArrayList<>();
        if (shop.getGstin() == null || shop.getGstin().isBlank()) {
            problems.add("Shop GSTIN is missing in settings");
        } else if (!shop.getGstin().trim().toUpperCase(Locale.ROOT).matches(GstStates.GSTIN_PATTERN)) {
            problems.add("Shop GSTIN format is invalid");
        }
        if (shop.getStateCode() == null || shop.getStateCode().isBlank()) {
            problems.add("Shop state code is missing in settings");
        } else if (!GstStates.isValidCode(shop.getStateCode())) {
            problems.add("Shop state code is invalid");
        }
        if (shop.getAddress() == null || shop.getAddress().isBlank()) {
            problems.add("Shop address is missing in settings");
        }
        if (shop.getPincode() == null || shop.getPincode().isBlank()) {
            problems.add("Shop pincode is missing in settings");
        } else if (!shop.getPincode().trim().matches("\\d{6}")) {
            problems.add("Shop pincode must be 6 digits");
        }
        return problems;
    }

    static List<String> validateInvoice(Invoice inv, EinvoiceJsonBuilder.EinvoiceParty buyer) {
        List<String> problems = new ArrayList<>();
        problems.addAll(validateBuyer(inv.getInvoiceNo(), buyer));
        for (InvoiceLine line : inv.getLines()) {
            if (line.getHsn() == null || line.getHsn().isBlank()) {
                problems.add(inv.getInvoiceNo() + ": line " + line.getLineNo() + " is missing HSN");
            }
        }
        return problems;
    }

    static List<String> validateCreditNote(CreditNote cn, EinvoiceJsonBuilder.EinvoiceParty buyer) {
        List<String> problems = new ArrayList<>();
        problems.addAll(validateBuyer(cn.getCnNo(), buyer));
        for (CreditNoteLine line : cn.getLines()) {
            if (line.getHsn() == null || line.getHsn().isBlank()) {
                problems.add(cn.getCnNo() + ": line " + line.getLineNo() + " is missing HSN");
            }
        }
        return problems;
    }

    private static List<String> validateBuyer(String docNo, EinvoiceJsonBuilder.EinvoiceParty buyer) {
        List<String> problems = new ArrayList<>();
        if (buyer.gstin() == null || buyer.gstin().isBlank()) {
            problems.add(docNo + ": buyer GSTIN is missing");
        } else if (!buyer.gstin().matches(GstStates.GSTIN_PATTERN)) {
            problems.add(docNo + ": buyer GSTIN format is invalid");
        }
        if (buyer.name() == null || buyer.name().isBlank()) {
            problems.add(docNo + ": buyer name is missing");
        }
        if (buyer.address() == null || buyer.address().isBlank()) {
            problems.add(docNo + ": buyer address is missing");
        }
        if (buyer.stateCode() == null || buyer.stateCode().isBlank()) {
            problems.add(docNo + ": buyer state code is missing");
        } else if (!GstStates.isValidCode(buyer.stateCode())) {
            problems.add(docNo + ": buyer state code is invalid");
        }
        if (buyer.pincode() == null || buyer.pincode().isBlank()) {
            problems.add(docNo + ": buyer pincode is missing (add it on the customer record)");
        } else if (!buyer.pincode().matches("\\d{6}")) {
            problems.add(docNo + ": buyer pincode must be 6 digits");
        }
        return problems;
    }
}
