package app.billing.common;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * RFC 9562 UUID version 7: 48-bit Unix millis + random bits.
 * Time-ordered, so B-tree indexes stay compact and IDs sort by creation time.
 * Safe to generate on any device without coordination (needed for future sync).
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {
    }

    public static UUID generate() {
        long millis = System.currentTimeMillis();
        byte[] rnd = new byte[10];
        RANDOM.nextBytes(rnd);

        long msb = (millis & 0xFFFF_FFFF_FFFFL) << 16;      // 48 bits timestamp
        msb |= 0x7000L;                                       // version 7
        msb |= ((rnd[0] & 0x0FL) << 8) | (rnd[1] & 0xFFL);    // 12 bits rand_a

        long lsb = 0x8000_0000_0000_0000L;                    // variant 10
        lsb |= (rnd[2] & 0x3FL) << 56;
        for (int i = 3; i < 10; i++) {
            lsb |= (rnd[i] & 0xFFL) << (8 * (9 - i));
        }
        return new UUID(msb, lsb);
    }
}
