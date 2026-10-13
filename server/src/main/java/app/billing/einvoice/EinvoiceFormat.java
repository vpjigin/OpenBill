package app.billing.einvoice;

import java.util.Locale;
import java.util.Map;

import app.billing.common.GstStates;

final class EinvoiceFormat {

    private static final Map<String, String> UQC = Map.ofEntries(
            Map.entry("NOS", "NOS"),
            Map.entry("PCS", "PCS"),
            Map.entry("BOX", "BOX"),
            Map.entry("KG", "KGS"),
            Map.entry("G", "GMS"),
            Map.entry("LTR", "LTR"),
            Map.entry("ML", "MLT"),
            Map.entry("MTR", "MTR"));

    private EinvoiceFormat() {
    }

    static String uqc(String unit) {
        if (unit == null || unit.isBlank()) {
            return "OTH";
        }
        String u = unit.trim().toUpperCase(Locale.ROOT);
        return UQC.getOrDefault(u, "OTH");
    }

    static String resolvePosCode(String customerGstin, String customerStateCode, boolean interState,
                                 String shopStateCode) {
        if (customerGstin != null && !customerGstin.isBlank()) {
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
}
