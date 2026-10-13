package app.billing.einvoice;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import app.billing.creditnote.CreditNote;
import app.billing.creditnote.CreditNoteLine;
import app.billing.invoice.Invoice;
import app.billing.invoice.InvoiceLine;
import app.billing.settings.ShopSettings;

/**
 * NIC e-invoice schema v1.1 field names — verify against the current official schema before filing.
 */
final class EinvoiceJsonBuilder {

    private static final ZoneId ZONE = ZoneId.of("Asia/Kolkata");
    private static final DateTimeFormatter DOC_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ENGLISH);

    private EinvoiceJsonBuilder() {
    }

    static Map<String, Object> invoiceDocument(Invoice inv, ShopSettings shop, EinvoiceParty buyer) {
        Map<String, Object> root = baseDocument(shop);
        root.put("TranDtls", tranDtls("B2B"));
        root.put("DocDtls", docDtls("INV", inv.getInvoiceNo(), inv.getInvoiceDate()));
        root.put("SellerDtls", sellerDtls(shop));
        root.put("BuyerDtls", buyerDtls(buyer));
        root.put("ItemList", itemListFromInvoice(inv.getLines()));
        root.put("ValDtls", valDtls(inv.getTaxablePaise(), inv.getCgstPaise(), inv.getSgstPaise(), inv.getIgstPaise(),
                inv.getRoundOffPaise(), inv.getTotalPaise()));
        return root;
    }

    static Map<String, Object> creditNoteDocument(CreditNote cn, Invoice original, ShopSettings shop,
                                                  EinvoiceParty buyer) {
        Map<String, Object> root = baseDocument(shop);
        root.put("TranDtls", tranDtls("B2B"));
        root.put("DocDtls", docDtls("CRN", cn.getCnNo(), cn.getCnDate()));
        root.put("SellerDtls", sellerDtls(shop));
        root.put("BuyerDtls", buyerDtls(buyer));
        root.put("ItemList", itemListFromCreditNote(cn.getLines()));
        root.put("ValDtls", valDtls(cn.getTaxablePaise(), cn.getCgstPaise(), cn.getSgstPaise(), cn.getIgstPaise(),
                cn.getRoundOffPaise(), cn.getTotalPaise()));
        root.put("PrecDocDtls", List.of(precDoc(original.getInvoiceNo(), original.getInvoiceDate())));
        return root;
    }

    private static Map<String, Object> baseDocument(ShopSettings shop) {
        Map<String, Object> root = new LinkedHashMap<>();
        root.put("Version", "1.1");
        return root;
    }

    private static Map<String, String> tranDtls(String supTyp) {
        Map<String, String> t = new LinkedHashMap<>();
        t.put("TaxSch", "GST");
        t.put("SupTyp", supTyp);
        return t;
    }

    private static Map<String, String> docDtls(String typ, String no, Instant when) {
        Map<String, String> d = new LinkedHashMap<>();
        d.put("Typ", typ);
        d.put("No", no);
        d.put("Dt", DOC_DATE.format(when.atZone(ZONE).toLocalDate()));
        return d;
    }

    private static Map<String, Object> sellerDtls(ShopSettings shop) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("Gstin", shop.getGstin().trim().toUpperCase(Locale.ROOT));
        s.put("LglNm", shop.getShopName());
        s.put("TrdNm", shop.getShopName());
        s.put("Addr1", nullToEmpty(shop.getAddress()));
        s.put("Loc", "");
        s.put("Pin", shop.getPincode().trim());
        s.put("Stcd", shop.getStateCode().trim());
        if (shop.getPhone() != null && !shop.getPhone().isBlank()) {
            s.put("Ph", shop.getPhone().trim());
        }
        return s;
    }

    private static Map<String, Object> buyerDtls(EinvoiceParty buyer) {
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("Gstin", buyer.gstin());
        b.put("LglNm", buyer.name());
        b.put("TrdNm", buyer.name());
        b.put("Pos", buyer.posStateCode());
        b.put("Addr1", nullToEmpty(buyer.address()));
        b.put("Loc", "");
        b.put("Pin", buyer.pincode());
        b.put("Stcd", buyer.stateCode());
        if (buyer.phone() != null && !buyer.phone().isBlank()) {
            b.put("Ph", buyer.phone());
        }
        return b;
    }

    private static List<Map<String, Object>> itemListFromInvoice(List<InvoiceLine> lines) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (InvoiceLine line : lines) {
            items.add(itemRow(line.getLineNo(), line.getName(), line.getHsn(), line.getQtyMilli(), line.getUnit(),
                    line.getRatePaise(), line.getGrossPaise(), line.getDiscountPaise(), line.getTaxablePaise(),
                    line.getGstRateBp(), line.getCgstPaise(), line.getSgstPaise(), line.getIgstPaise(),
                    line.getTotalPaise()));
        }
        return items;
    }

    private static List<Map<String, Object>> itemListFromCreditNote(List<CreditNoteLine> lines) {
        List<Map<String, Object>> items = new ArrayList<>();
        for (CreditNoteLine line : lines) {
            long gross = line.getTaxablePaise() + line.getCgstPaise() + line.getSgstPaise() + line.getIgstPaise();
            items.add(itemRow(line.getLineNo(), line.getName(), line.getHsn(), line.getQtyMilli(), line.getUnit(),
                    line.getRatePaise(), gross, 0, line.getTaxablePaise(), line.getGstRateBp(), line.getCgstPaise(),
                    line.getSgstPaise(), line.getIgstPaise(), line.getTotalPaise()));
        }
        return items;
    }

    private static Map<String, Object> itemRow(int slNo, String name, String hsn, long qtyMilli, String unit,
                                             long ratePaise, long totAmtPaise, long discountPaise, long assAmtPaise,
                                             int gstRateBp, long cgst, long sgst, long igst, long totItemValPaise) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("SlNo", String.valueOf(slNo));
        row.put("PrdDesc", name);
        row.put("IsServc", "N");
        row.put("HsnCd", hsn == null ? "" : hsn.trim());
        row.put("Qty", EinvoiceAmounts.quantity(qtyMilli));
        row.put("Unit", EinvoiceFormat.uqc(unit));
        row.put("UnitPrice", EinvoiceAmounts.rupees(ratePaise));
        row.put("TotAmt", EinvoiceAmounts.rupees(totAmtPaise));
        row.put("Discount", EinvoiceAmounts.rupees(discountPaise));
        row.put("AssAmt", EinvoiceAmounts.rupees(assAmtPaise));
        row.put("GstRt", EinvoiceAmounts.gstRatePercent(gstRateBp));
        row.put("IgstAmt", EinvoiceAmounts.rupees(igst));
        row.put("CgstAmt", EinvoiceAmounts.rupees(cgst));
        row.put("SgstAmt", EinvoiceAmounts.rupees(sgst));
        row.put("TotItemVal", EinvoiceAmounts.rupees(totItemValPaise));
        return row;
    }

    private static Map<String, Object> valDtls(long assVal, long cgst, long sgst, long igst, long rndOff,
                                               long totInv) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("AssVal", EinvoiceAmounts.rupees(assVal));
        v.put("CgstVal", EinvoiceAmounts.rupees(cgst));
        v.put("SgstVal", EinvoiceAmounts.rupees(sgst));
        v.put("IgstVal", EinvoiceAmounts.rupees(igst));
        v.put("RndOffAmt", EinvoiceAmounts.rupees(rndOff));
        v.put("TotInvVal", EinvoiceAmounts.rupees(totInv));
        return v;
    }

    private static Map<String, String> precDoc(String invNo, Instant invDate) {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("InvNo", invNo);
        p.put("InvDt", DOC_DATE.format(invDate.atZone(ZONE).toLocalDate()));
        return p;
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s.trim();
    }

    record EinvoiceParty(String gstin, String name, String address, String stateCode, String posStateCode,
                         String pincode, String phone) {
    }
}
