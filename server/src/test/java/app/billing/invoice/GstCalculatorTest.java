package app.billing.invoice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

class GstCalculatorTest {

    private static GstCalculator.LineInput line(long rate, long qtyMilli, int bp, boolean inclusive,
                                                long discount) {
        return new GstCalculator.LineInput(rate, qtyMilli, bp, inclusive, discount);
    }

    @Test
    void intraStateSplitsIntoCgstAndSgst() {
        // 2 x Rs 1199 @ 5% -> taxable 2398.00, CGST 59.95, SGST 59.95
        var r = GstCalculator.calculate(List.of(line(119_900, 2000, 500, false, 0)), false, 0);
        assertThat(r.taxable()).isEqualTo(239_800);
        assertThat(r.cgst()).isEqualTo(5_995);
        assertThat(r.sgst()).isEqualTo(5_995);
        assertThat(r.igst()).isZero();
        assertThat(r.total() % 100).isZero();
        assertThat(r.total() - r.roundOff()).isEqualTo(239_800 + 5_995 * 2);
    }

    @Test
    void interStateUsesIgst() {
        var r = GstCalculator.calculate(List.of(line(45_000, 1000, 1200, false, 0)), true, 0);
        assertThat(r.igst()).isEqualTo(5_400);
        assertThat(r.cgst()).isZero();
        assertThat(r.total()).isEqualTo(50_400);
        assertThat(r.roundOff()).isZero();
    }

    @Test
    void taxInclusiveIntraState() {
        // Rs 118 incl. 18% -> taxable 100.00, CGST 9.00, SGST 9.00, total 118.00
        var r = GstCalculator.calculate(List.of(line(11_800, 1000, 1800, true, 0)), false, 0);
        assertThat(r.lines().getFirst().taxable()).isEqualTo(10_000);
        assertThat(r.lines().getFirst().cgst()).isEqualTo(900);
        assertThat(r.lines().getFirst().sgst()).isEqualTo(900);
        assertThat(r.lines().getFirst().total()).isEqualTo(11_800);
        assertThat(r.total()).isEqualTo(11_800);
        assertThat(r.roundOff()).isZero();
    }

    @Test
    void taxInclusiveOddTaxPaiseGivesExtraToCgst() {
        // Rs 1.03 incl. 18% -> tax 16 paise -> CGST 8, SGST 8
        var r = GstCalculator.calculate(List.of(line(103, 1000, 1800, true, 0)), false, 0);
        assertThat(r.lines().getFirst().cgst() + r.lines().getFirst().sgst())
                .isEqualTo(r.lines().getFirst().total() - r.lines().getFirst().taxable());
        assertThat(r.lines().getFirst().cgst()).isGreaterThanOrEqualTo(r.lines().getFirst().sgst());
    }

    @Test
    void decimalQuantity() {
        // 1.250 kg x Rs 80.00 = Rs 100.00 gross
        var r = GstCalculator.calculate(List.of(line(8_000, 1250, 0, false, 0)), false, 0);
        assertThat(r.lines().getFirst().gross()).isEqualTo(10_000);
        assertThat(r.taxable()).isEqualTo(10_000);
    }

    @Test
    void lineDiscountReducesTaxable() {
        var r = GstCalculator.calculate(List.of(line(10_000, 1000, 500, false, 1_000)), false, 0);
        assertThat(r.lines().getFirst().gross()).isEqualTo(10_000);
        assertThat(r.lines().getFirst().discount()).isEqualTo(1_000);
        assertThat(r.lines().getFirst().taxable()).isEqualTo(9_000);
    }

    @Test
    void billDiscountAllocatedExactlyAcrossUnevenLines() {
        var lines = List.of(
                line(10_000, 1000, 0, false, 0),
                line(20_000, 1000, 0, false, 0),
                line(30_000, 1000, 0, false, 0));
        var r = GstCalculator.calculate(lines, false, 1_000);
        long allocSum = r.lines().stream().mapToLong(GstCalculator.LineResult::discount).sum();
        assertThat(allocSum).isEqualTo(1_000);
        assertThat(r.discount()).isEqualTo(1_000);
        assertThat(r.taxable()).isEqualTo(60_000 - 1_000);
    }

    @Test
    void discountGreaterThanGrossThrows() {
        assertThatThrownBy(() -> GstCalculator.calculate(
                List.of(line(100, 1000, 500, false, 200)), false, 0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void roundOffGoesToNearestRupeeUp() {
        // 3 x Rs 85 @ 18% -> 255.00 + 22.95 + 22.95 = 300.90 -> 301.00
        var r = GstCalculator.calculate(List.of(line(8_500, 3000, 1800, false, 0)), false, 0);
        assertThat(r.total()).isEqualTo(30_100);
        assertThat(r.roundOff()).isEqualTo(10);
    }

    @Test
    void roundOffGoesToNearestRupeeDown() {
        // Construct lines whose exact total ends just below a rupee boundary.
        var r = GstCalculator.calculate(List.of(line(33, 1000, 0, false, 0)), false, 0);
        assertThat(r.total()).isEqualTo(0);
        assertThat(r.roundOff()).isEqualTo(-33);
    }

    @Test
    void financialYearStartsInApril() {
        assertThat(InvoiceService.financialYear(LocalDate.of(2026, 10, 12))).isEqualTo("26-27");
        assertThat(InvoiceService.financialYear(LocalDate.of(2027, 3, 31))).isEqualTo("26-27");
        assertThat(InvoiceService.financialYear(LocalDate.of(2027, 4, 1))).isEqualTo("27-28");
        assertThat(InvoiceService.financialYear(LocalDate.of(2099, 12, 1))).isEqualTo("99-00");
    }
}
