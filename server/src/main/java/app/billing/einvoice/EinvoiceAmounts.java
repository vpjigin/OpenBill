package app.billing.einvoice;

import java.math.BigDecimal;
import java.math.RoundingMode;

final class EinvoiceAmounts {

    private EinvoiceAmounts() {
    }

    static double rupees(long paise) {
        return BigDecimal.valueOf(paise, 2).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    static double gstRatePercent(int gstRateBp) {
        return BigDecimal.valueOf(gstRateBp, 2).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    static double quantity(long qtyMilli) {
        return BigDecimal.valueOf(qtyMilli, 3).setScale(3, RoundingMode.HALF_UP).doubleValue();
    }
}
