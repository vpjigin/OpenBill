package app.billing.item;

import java.util.Set;

public final class Units {

    private static final Set<String> DECIMAL_UNITS = Set.of("KG", "G", "LTR", "ML", "MTR");

    private Units() {
    }

    public static boolean isDecimal(String unit) {
        if (unit == null || unit.isBlank()) {
            return false;
        }
        return DECIMAL_UNITS.contains(unit.trim().toUpperCase());
    }
}
