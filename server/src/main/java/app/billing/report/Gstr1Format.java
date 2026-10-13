package app.billing.report;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.Map;

import app.billing.common.GstStates;

final class Gstr1Format {

    /** B2CL threshold — ₹1,00,000; verify against the current GST notification. */
    public static final long B2CL_LIMIT_PAISE = 10_000_000L;

    private static final ZoneId ZONE = ZoneId.systemDefault();
    private static final DateTimeFormatter INVOICE_DATE =
            DateTimeFormatter.ofPattern("dd-MMM-yyyy", Locale.ENGLISH);

    private static final Map<String, String> UQC = Map.ofEntries(
            Map.entry("NOS", "NOS"),
            Map.entry("PCS", "PCS"),
            Map.entry("BOX", "BOX"),
            Map.entry("KG", "KGS"),
            Map.entry("G", "GMS"),
            Map.entry("LTR", "LTR"),
            Map.entry("ML", "MLT"),
            Map.entry("MTR", "MTR"));

    private Gstr1Format() {
    }

    static String formatDate(Instant instant) {
        LocalDate d = instant.atZone(ZONE).toLocalDate();
        return INVOICE_DATE.format(d);
    }

    static String rupees(long paise) {
        BigDecimal rupees = BigDecimal.valueOf(paise, 2);
        return rupees.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    static String ratePercent(int gstRateBp) {
        BigDecimal pct = BigDecimal.valueOf(gstRateBp, 2);
        return pct.stripTrailingZeros().toPlainString();
    }

    static String quantity(long qtyMilli) {
        return BigDecimal.valueOf(qtyMilli, 3).stripTrailingZeros().toPlainString();
    }

    static String placeOfSupply(String stateCode) {
        if (stateCode == null || stateCode.isBlank()) {
            return "";
        }
        String code = stateCode.trim();
        String name = GstStates.nameOf(code);
        if (name == null) {
            return code;
        }
        return code + "-" + name;
    }

    static String uqc(String unit) {
        if (unit == null || unit.isBlank()) {
            return "OTH";
        }
        String u = unit.trim().toUpperCase(Locale.ROOT);
        return UQC.getOrDefault(u, "OTH");
    }

    static boolean hasGstin(String gstin) {
        return gstin != null && !gstin.isBlank();
    }

    static boolean isB2cl(boolean interState, long invoiceTotalPaise, String customerGstin) {
        return !hasGstin(customerGstin) && interState && invoiceTotalPaise > B2CL_LIMIT_PAISE;
    }

    static String resolvePosCode(String customerGstin, String customerStateCode, boolean interState, String shopStateCode) {
        if (hasGstin(customerGstin)) {
            return GstStates.stateCodeFromGstin(customerGstin.trim().toUpperCase(Locale.ROOT))
                    .orElse(normalizeState(customerStateCode, shopStateCode));
        }
        if (interState && customerStateCode != null && !customerStateCode.isBlank()) {
            return customerStateCode.trim();
        }
        return normalizeState(shopStateCode, "32");
    }

    private static String normalizeState(String primary, String fallback) {
        if (primary != null && !primary.isBlank()) {
            return primary.trim();
        }
        return fallback != null ? fallback.trim() : "";
    }

    static String filenameSlug(String gstin, String shopName) {
        if (gstin != null && !gstin.isBlank()) {
            return gstin.trim().toUpperCase(Locale.ROOT).replaceAll("[^0-9A-Z]", "");
        }
        String name = shopName != null ? shopName.trim() : "shop";
        String slug = name.replaceAll("[^A-Za-z0-9]+", "-").replaceAll("^-|-$", "");
        return slug.isEmpty() ? "shop" : slug;
    }
}
