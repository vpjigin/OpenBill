package app.billing.print;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EscPosBuilderTest {

    private static String hex(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b & 0xFF));
        }
        return sb.toString();
    }

    @Test
    void initAlignTextFeedCutAndDrawerGoldenBytes() {
        byte[] receipt = new EscPosBuilder(32).init().alignCenter().bold(true).text("Hi").bold(false).alignLeft()
                .textLine("Line").separator('-').feed(2).cut().build();
        assertThat(hex(receipt)).isEqualTo(
                "1b401b61011b450148691b45001b6100" // init, center, bold on, "Hi", bold off, left
                        + "4c696e650a" // Line\n
                        + "2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d2d0a" // 32 dashes
                        + "0a0a1d5600"); // feed feed cut

        byte[] drawer = new EscPosBuilder(32).init().openDrawer().build();
        assertThat(hex(drawer)).isEqualTo("1b401b700019fa");
    }

    @Test
    void columnsPadLeftAndRight() {
        byte[] line = new EscPosBuilder(10).init().columns("A", "9.00").build();
        assertThat(new String(line)).contains("A").contains("9.00");
    }

    @Test
    void rupeeInTextBecomesRs() {
        byte[] bytes = new EscPosBuilder(32).init().textLine("Price ₹100").build();
        assertThat(new String(bytes)).contains("Rs.100");
    }
}
