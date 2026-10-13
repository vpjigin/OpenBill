package app.billing.print;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
/** Pure ESC/POS byte builder (CP437 / ASCII text). */
public final class EscPosBuilder {

    private static final Charset CP437 = Charset.forName("Cp437");

    private final ByteArrayOutputStream out = new ByteArrayOutputStream();
    private final int charsPerLine;

    public EscPosBuilder(int charsPerLine) {
        this.charsPerLine = Math.max(16, charsPerLine);
    }

    public EscPosBuilder init() {
        write(0x1B, 0x40);
        return this;
    }

    public EscPosBuilder alignLeft() {
        write(0x1B, 0x61, 0x00);
        return this;
    }

    public EscPosBuilder alignCenter() {
        write(0x1B, 0x61, 0x01);
        return this;
    }

    public EscPosBuilder alignRight() {
        write(0x1B, 0x61, 0x02);
        return this;
    }

    public EscPosBuilder bold(boolean on) {
        write(0x1B, 0x45, on ? 0x01 : 0x00);
        return this;
    }

    public EscPosBuilder doubleHeight(boolean on) {
        write(0x1B, 0x21, on ? 0x10 : 0x00);
        return this;
    }

    public EscPosBuilder text(String text) {
        byte[] bytes = EscPosText.encode(text);
        write(bytes);
        return this;
    }

    public EscPosBuilder textLine(String text) {
        return text(text). feed(1);
    }

    public EscPosBuilder columns(String left, String right) {
        String l = EscPosText.truncate(left, charsPerLine / 2);
        String r = EscPosText.truncate(right, charsPerLine / 2);
        int pad = charsPerLine - l.length() - r.length();
        if (pad < 1) {
            pad = 1;
        }
        return textLine(l + " ".repeat(pad) + r);
    }

    public EscPosBuilder separator(char ch) {
        return textLine(String.valueOf(ch).repeat(charsPerLine));
    }

    public EscPosBuilder feed(int lines) {
        for (int i = 0; i < lines; i++) {
            write(0x0A);
        }
        return this;
    }

    public EscPosBuilder cut() {
        write(0x1D, 0x56, 0x00);
        return this;
    }

    /** Cash drawer kick: ESC p 0 25 250 */
    public EscPosBuilder openDrawer() {
        write(0x1B, 0x70, 0x00, 0x19, 0xFA);
        return this;
    }

    /** QR code (model 2), optional — stores up to ~200 chars. */
    public EscPosBuilder qrCode(String data) {
        if (data == null || data.isBlank()) {
            return this;
        }
        byte[] content = EscPosText.encode(data);
        int len = content.length + 3;
        int pL = len & 0xFF;
        int pH = (len >> 8) & 0xFF;
        // Select QR model 2
        write(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00);
        // Module size 4
        write(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, 0x04);
        // Error correction M
        write(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, 0x31);
        // Store data
        write(0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30);
        write(content);
        // Print
        write(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30);
        return feed(1);
    }

    public byte[] build() {
        return out.toByteArray();
    }

    private void write(int... bytes) {
        for (int b : bytes) {
            out.write(b & 0xFF);
        }
    }

    private void write(byte[] bytes) {
        try {
            out.write(bytes);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    public static final class EscPosText {

        private EscPosText() {
        }

        static String sanitize(String s) {
            if (s == null) {
                return "";
            }
            String t = s.replace("₹", "Rs.");
            StringBuilder sb = new StringBuilder(t.length());
            for (int i = 0; i < t.length(); i++) {
                char c = t.charAt(i);
                if (c == '\n' || c == '\r') {
                    sb.append(' ');
                } else if (c < 128) {
                    sb.append(c);
                } else {
                    sb.append('?');
                }
            }
            return sb.toString();
        }

        static byte[] encode(String s) {
            return sanitize(s).getBytes(CP437);
        }

        static String truncate(String s, int max) {
            String t = sanitize(s);
            if (t.length() <= max) {
                return t;
            }
            return t.substring(0, Math.max(0, max - 1)) + "…";
        }

        static String money(long paise) {
            long rupees = paise / 100;
            long p = Math.abs(paise % 100);
            return "Rs." + rupees + "." + String.format("%02d", p);
        }

        static String qty(long qtyMilli, String unit) {
            if (app.billing.item.Units.isDecimal(unit)) {
                long whole = qtyMilli / 1000;
                long frac = qtyMilli % 1000;
                if (frac == 0) {
                    return whole + " " + unit;
                }
                String f = String.format("%03d", frac).replaceAll("0+$", "");
                return whole + "." + f + " " + unit;
            }
            long q = qtyMilli / 1000;
            return q + " " + unit;
        }
    }

}
