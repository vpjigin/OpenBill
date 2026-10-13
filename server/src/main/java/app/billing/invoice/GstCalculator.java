package app.billing.invoice;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Pure GST math, all in paise. Supports decimal quantities (milli-units), tax-inclusive
 * pricing, line and bill-level discounts.
 */
public final class GstCalculator {

    private GstCalculator() {
    }

    public record LineInput(long ratePaise, long qtyMilli, int gstRateBp, boolean taxInclusive,
                            long discountPaise) {
    }

    public record LineResult(long gross, long discount, long taxable, long cgst, long sgst, long igst,
                             long total) {
    }

    public record Result(List<LineResult> lines, long gross, long discount, long taxable, long cgst,
                         long sgst, long igst, long roundOff, long total) {
    }

    public static Result calculate(List<LineInput> lines, boolean interState, long billDiscountPaise) {
        if (billDiscountPaise < 0) {
            throw new IllegalArgumentException("bill discount must not be negative");
        }

        int n = lines.size();
        long[] grossArr = new long[n];
        long[] netArr = new long[n];
        long[] lineDiscountArr = new long[n];

        for (int i = 0; i < n; i++) {
            LineInput in = lines.get(i);
            validateLineInput(in);
            long gross = roundHalfUp(in.ratePaise() * in.qtyMilli(), 1000);
            grossArr[i] = gross;
            long lineDisc = in.discountPaise();
            long net = gross - lineDisc;
            netArr[i] = net;
            lineDiscountArr[i] = lineDisc;
        }

        long sumNet = 0;
        for (long net : netArr) {
            sumNet += net;
        }
        if (billDiscountPaise > sumNet) {
            throw new IllegalArgumentException("bill discount exceeds net line value");
        }

        long[] billAlloc = allocateBillDiscount(netArr, billDiscountPaise);

        List<LineResult> lineResults = new ArrayList<>(n);
        long sumGross = 0, sumDiscount = 0, sumTaxable = 0, sumCgst = 0, sumSgst = 0, sumIgst = 0;
        long exactTotal = 0;

        for (int i = 0; i < n; i++) {
            LineInput in = lines.get(i);
            long gross = grossArr[i];
            long discount = lineDiscountArr[i] + billAlloc[i];
            long net = gross - discount;

            long taxable;
            long cgst = 0, sgst = 0, igst = 0;
            long lineTotal;

            if (in.taxInclusive()) {
                taxable = roundHalfUp(net * 10_000L, 10_000L + in.gstRateBp());
                long tax = net - taxable;
                if (interState) {
                    igst = tax;
                } else {
                    cgst = ceilDiv(tax, 2);
                    sgst = tax - cgst;
                }
                lineTotal = net;
            } else {
                taxable = net;
                if (interState) {
                    igst = roundHalfUp(taxable * in.gstRateBp(), 10_000L);
                } else {
                    cgst = roundHalfUp(taxable * in.gstRateBp(), 20_000L);
                    sgst = cgst;
                }
                lineTotal = taxable + cgst + sgst + igst;
            }

            lineResults.add(new LineResult(gross, discount, taxable, cgst, sgst, igst, lineTotal));
            sumGross += gross;
            sumDiscount += discount;
            sumTaxable += taxable;
            sumCgst += cgst;
            sumSgst += sgst;
            sumIgst += igst;
            exactTotal += lineTotal;
        }

        long roundedTotal = roundHalfUp(exactTotal, 100) * 100;
        long roundOff = roundedTotal - exactTotal;

        return new Result(lineResults, sumGross, sumDiscount, sumTaxable, sumCgst, sumSgst, sumIgst,
                roundOff, roundedTotal);
    }

    private static void validateLineInput(LineInput in) {
        if (in.ratePaise() < 0) {
            throw new IllegalArgumentException("rate must not be negative");
        }
        if (in.qtyMilli() <= 0) {
            throw new IllegalArgumentException("quantity must be positive");
        }
        if (in.gstRateBp() < 0) {
            throw new IllegalArgumentException("GST rate must not be negative");
        }
        if (in.discountPaise() < 0) {
            throw new IllegalArgumentException("line discount must not be negative");
        }
        long gross = roundHalfUp(in.ratePaise() * in.qtyMilli(), 1000);
        if (in.discountPaise() > gross) {
            throw new IllegalArgumentException("line discount exceeds gross");
        }
    }

    /** Largest-remainder proportional split; ties go to earlier lines. */
    static long[] allocateBillDiscount(long[] nets, long billDiscountPaise) {
        int n = nets.length;
        long[] alloc = new long[n];
        if (billDiscountPaise == 0 || n == 0) {
            return alloc;
        }
        long totalNet = 0;
        for (long net : nets) {
            totalNet += net;
        }

        BigDecimal bdBill = BigDecimal.valueOf(billDiscountPaise);
        BigDecimal bdTotal = BigDecimal.valueOf(totalNet);
        long assigned = 0;
        record Remainder(int index, BigDecimal fraction) {
        }
        List<Remainder> remainders = new ArrayList<>(n);

        for (int i = 0; i < n; i++) {
            BigDecimal exact = bdBill.multiply(BigDecimal.valueOf(nets[i]))
                    .divide(bdTotal, 10, RoundingMode.HALF_UP);
            long floor = exact.setScale(0, RoundingMode.FLOOR).longValue();
            alloc[i] = floor;
            assigned += floor;
            remainders.add(new Remainder(i, exact.subtract(BigDecimal.valueOf(floor))));
        }

        long leftover = billDiscountPaise - assigned;
        remainders.sort(Comparator
                .comparing(Remainder::fraction).reversed()
                .thenComparingInt(Remainder::index));
        for (int k = 0; k < leftover; k++) {
            alloc[remainders.get(k).index()]++;
        }
        return alloc;
    }

    /** Numerator / denominator, half-up to integer. */
    public static long roundHalfUp(long numerator, long denominator) {
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    static long ceilDiv(long amount, long divisor) {
        return BigDecimal.valueOf(amount)
                .divide(BigDecimal.valueOf(divisor), 0, RoundingMode.CEILING)
                .longValueExact();
    }
}
