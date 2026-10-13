package app.billing.item;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class Ean13Test {

    @Test
    void checkDigitMatchesKnownCodes() {
        assertThat(Ean13.checkDigit("400638133393")).isEqualTo(1);
        assertThat(Ean13.checkDigit("590123412345")).isEqualTo(7);
        assertThat(Ean13.checkDigit("200000000000")).isEqualTo(8);
    }

    @Test
    void formatInternalUsesPrefixAndCheckDigit() {
        assertThat(Ean13.formatInternal(1)).isEqualTo("2000000000015");
        assertThat(Ean13.isValid(Ean13.formatInternal(42))).isTrue();
    }
}
