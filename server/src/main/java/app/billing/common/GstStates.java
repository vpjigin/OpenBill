package app.billing.common;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/** Indian GST state / UT codes (first two digits of GSTIN). */
public final class GstStates {

    private static final Map<String, String> CODE_TO_NAME;

    static {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("01", "Jammu and Kashmir");
        m.put("02", "Himachal Pradesh");
        m.put("03", "Punjab");
        m.put("04", "Chandigarh");
        m.put("05", "Uttarakhand");
        m.put("06", "Haryana");
        m.put("07", "Delhi");
        m.put("08", "Rajasthan");
        m.put("09", "Uttar Pradesh");
        m.put("10", "Bihar");
        m.put("11", "Sikkim");
        m.put("12", "Arunachal Pradesh");
        m.put("13", "Nagaland");
        m.put("14", "Manipur");
        m.put("15", "Mizoram");
        m.put("16", "Tripura");
        m.put("17", "Meghalaya");
        m.put("18", "Assam");
        m.put("19", "West Bengal");
        m.put("20", "Jharkhand");
        m.put("21", "Odisha");
        m.put("22", "Chhattisgarh");
        m.put("23", "Madhya Pradesh");
        m.put("24", "Gujarat");
        m.put("26", "Dadra and Nagar Haveli and Daman and Diu");
        m.put("27", "Maharashtra");
        m.put("29", "Karnataka");
        m.put("30", "Goa");
        m.put("31", "Lakshadweep");
        m.put("32", "Kerala");
        m.put("33", "Tamil Nadu");
        m.put("34", "Puducherry");
        m.put("35", "Andaman and Nicobar Islands");
        m.put("36", "Telangana");
        m.put("37", "Andhra Pradesh");
        m.put("38", "Ladakh");
        m.put("97", "Other Territory");
        CODE_TO_NAME = Collections.unmodifiableMap(m);
    }

    public static final String GSTIN_PATTERN = "^\\d{2}[A-Z]{5}\\d{4}[A-Z][1-9A-Z]Z[0-9A-Z]$";

    private GstStates() {
    }

    public static Map<String, String> all() {
        return CODE_TO_NAME;
    }

    public static boolean isValidCode(String code) {
        if (code == null || code.isBlank()) {
            return false;
        }
        return CODE_TO_NAME.containsKey(code.trim());
    }

    public static String nameOf(String code) {
        if (code == null) {
            return null;
        }
        return CODE_TO_NAME.get(code.trim());
    }

    public static Optional<String> stateCodeFromGstin(String gstin) {
        if (gstin == null || gstin.isBlank()) {
            return Optional.empty();
        }
        String g = gstin.trim().toUpperCase();
        if (!g.matches(GSTIN_PATTERN)) {
            return Optional.empty();
        }
        String code = g.substring(0, 2);
        return isValidCode(code) ? Optional.of(code) : Optional.empty();
    }
}
