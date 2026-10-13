package app.billing.shop;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class ShopLogoServiceTest {

    @Test
    void detectsPngAndJpegMagicBytes() {
        byte[] png = new byte[] {(byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0};
        byte[] jpeg = new byte[] {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00};
        assertThat(ShopLogoService.detectMime(png)).isEqualTo("image/png");
        assertThat(ShopLogoService.detectMime(jpeg)).isEqualTo("image/jpeg");
        assertThat(ShopLogoService.detectMime(new byte[] {0, 1, 2})).isNull();
    }
}
