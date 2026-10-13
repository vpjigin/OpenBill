package app.billing.item;

/** GS1 EAN-13 check digit and internal in-store codes (prefix {@code 200}). */
public final class Ean13 {

    public static final String INTERNAL_PREFIX = "200";

    private Ean13() {
    }

    /** Check digit for the first 12 digits (left to right). */
    public static int checkDigit(CharSequence twelveDigits) {
        if (twelveDigits.length() != 12) {
            throw new IllegalArgumentException("EAN-13 needs 12 data digits");
        }
        int sum = 0;
        for (int i = 0; i < 12; i++) {
            int d = digitAt(twelveDigits, i);
            sum += (i % 2 == 0) ? d : d * 3;
        }
        return (10 - (sum % 10)) % 10;
    }

    public static String formatInternal(long sequence) {
        if (sequence < 0 || sequence > 999_999_999L) {
            throw new IllegalArgumentException("Sequence out of range");
        }
        String body = INTERNAL_PREFIX + String.format("%09d", sequence);
        return body + checkDigit(body);
    }

    public static boolean isValid(String code) {
        if (code == null || code.length() != 13 || !code.chars().allMatch(Character::isDigit)) {
            return false;
        }
        int expected = checkDigit(code.subSequence(0, 12));
        return expected == digitAt(code, 12);
    }

    private static int digitAt(CharSequence s, int index) {
        char c = s.charAt(index);
        if (c < '0' || c > '9') {
            throw new IllegalArgumentException("Non-digit in EAN-13");
        }
        return c - '0';
    }
}
