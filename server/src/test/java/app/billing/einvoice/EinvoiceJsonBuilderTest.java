package app.billing.einvoice;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import app.billing.einvoice.EinvoiceJsonBuilder.EinvoiceParty;
import app.billing.invoice.Invoice;
import app.billing.invoice.InvoiceLine;
import app.billing.invoice.PaymentMode;
import app.billing.settings.ShopSettings;

class EinvoiceJsonBuilderTest {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final ObjectMapper JSON = new ObjectMapper();

    @Test
    void intraStateB2bInvoiceMatchesSnapshot() throws Exception {
        ShopSettings shop = shop("32AAACL1234C1Z5", "32", "695001");
        Invoice inv = invoice("INV/26-27/000101", LocalDate.of(2026, 1, 15), false);
        inv.addLine(line(1, "Widget A", "8471", 2000, "NOS", 100_00, 500, 200_00, 18_00, 18_00, 0, 236_00));
        inv.setTaxablePaise(200_00);
        inv.setCgstPaise(18_00);
        inv.setSgstPaise(18_00);
        inv.setTotalPaise(236_00);
        inv.setGrossPaise(205_00);
        inv.setDiscountPaise(500);

        EinvoiceParty buyer = new EinvoiceParty("32AABCU9603R1ZM", "Urban Traders", "12 MG Road", "32", "32", "695002",
                "9876543210");
        Map<String, Object> doc = EinvoiceJsonBuilder.invoiceDocument(inv, shop, buyer);

        String expected = resource("/einvoice/intra-state-invoice.json");
        assertJsonEquals(expected, JSON.writeValueAsString(List.of(doc)));
    }

    @Test
    void interStateB2bInvoiceMatchesSnapshot() throws Exception {
        ShopSettings shop = shop("32AAACL1234C1Z5", "32", "695001");
        Invoice inv = invoice("INV/26-27/000102", LocalDate.of(2026, 1, 16), true);
        inv.addLine(line(1, "Cable roll", "8544", 1000, "MTR", 450_00, 0, 450_00, 0, 0, 54_00, 504_00));
        inv.setTaxablePaise(450_00);
        inv.setIgstPaise(54_00);
        inv.setTotalPaise(504_00);
        inv.setGrossPaise(450_00);

        EinvoiceParty buyer = new EinvoiceParty("29AABCT1332L1Z6", "Tech Karnataka", "Indiranagar", "29", "29",
                "560038", null);
        Map<String, Object> doc = EinvoiceJsonBuilder.invoiceDocument(inv, shop, buyer);

        String expected = resource("/einvoice/inter-state-invoice.json");
        assertJsonEquals(expected, JSON.writeValueAsString(List.of(doc)));
    }

    @Test
    void validationFlagsMissingBuyerPincode() {
        ShopSettings shop = shop("32AAACL1234C1Z5", "32", "695001");
        shop.setAddress("Shop street");
        List<String> shopProblems = EinvoiceValidator.validateShop(shop);
        assertThat(shopProblems).isEmpty();

        EinvoiceParty buyer = new EinvoiceParty("32AABCU9603R1ZM", "Urban Traders", "12 MG Road", "32", "32", null,
                null);
        Invoice inv = invoice("INV/26-27/000103", LocalDate.of(2026, 1, 17), false);
        inv.addLine(line(1, "Item", "8471", 1000, "NOS", 100_00, 0, 100_00, 9_00, 9_00, 0, 118_00));

        List<String> problems = EinvoiceValidator.validateInvoice(inv, buyer);
        assertThat(problems).anyMatch(p -> p.contains("pincode"));
    }

    private static ShopSettings shop(String gstin, String state, String pin) {
        ShopSettings s = new ShopSettings();
        s.setShopName("Lighthouse Stores");
        s.setGstin(gstin);
        s.setStateCode(state);
        s.setAddress("Beach Road, Trivandrum");
        s.setPhone("4711234567");
        s.setPincode(pin);
        return s;
    }

    private static Invoice invoice(String no, LocalDate date, boolean inter) {
        Invoice inv = new Invoice();
        inv.setInvoiceNo(no);
        inv.setInvoiceDate(date.atStartOfDay(ZONE).toInstant());
        inv.setInterState(inter);
        inv.setPaymentMode(PaymentMode.CASH);
        inv.setCustomerGstin("32AABCU9603R1ZM");
        inv.setCustomerStateCode(inter ? "29" : "32");
        inv.setCustomerName("Buyer");
        inv.setCustomerAddress("Buyer address");
        inv.setTaxablePaise(0);
        inv.setCgstPaise(0);
        inv.setSgstPaise(0);
        inv.setIgstPaise(0);
        inv.setRoundOffPaise(0);
        inv.setTotalPaise(0);
        inv.setGrossPaise(0);
        inv.setDiscountPaise(0);
        inv.setBillDiscountPaise(0);
        return inv;
    }

    private static InvoiceLine line(int no, String name, String hsn, long qtyMilli, String unit, long rate,
                                    long discount, long taxable, long cgst, long sgst, long igst, long total) {
        InvoiceLine l = new InvoiceLine();
        l.setLineNo(no);
        l.setName(name);
        l.setHsn(hsn);
        l.setQtyMilli(qtyMilli);
        l.setUnit(unit);
        l.setRatePaise(rate);
        l.setDiscountPaise(discount);
        l.setGrossPaise(taxable + discount);
        l.setTaxablePaise(taxable);
        l.setGstRateBp(1200);
        l.setCgstPaise(cgst);
        l.setSgstPaise(sgst);
        l.setIgstPaise(igst);
        l.setTotalPaise(total);
        l.setTaxInclusive(false);
        return l;
    }

    private static void assertJsonEquals(String expectedJson, String actualJson) throws Exception {
        assertThat(JSON.readTree(actualJson)).isEqualTo(JSON.readTree(expectedJson));
    }

    private static String resource(String path) throws Exception {
        try (var in = EinvoiceJsonBuilderTest.class.getResourceAsStream(path)) {
            if (in == null) {
                throw new IllegalStateException("Missing test resource " + path);
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
