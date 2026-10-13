package app.billing.admin;

import java.util.HexFormat;
import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import app.billing.config.AppProperties;

@Component
public class AdminDesktopAuth {

    private static final Logger log = LoggerFactory.getLogger(AdminDesktopAuth.class);
    private static final AtomicBoolean WARNED = new AtomicBoolean();
    static final String COOKIE_NAME = "admin_desktop";

    private final boolean locked;
    private final byte[] secretBytes;
    private final String secretHex;

    public AdminDesktopAuth(AppProperties props) {
        String raw = props.adminSecret();
        if (raw == null || raw.isBlank()) {
            locked = false;
            secretBytes = null;
            secretHex = null;
            if (WARNED.compareAndSet(false, true)) {
                log.warn("APP_ADMIN_SECRET is not set; admin UI is not locked to the desktop window.");
            }
            return;
        }
        byte[] decoded = decodeHex(raw.trim());
        if (decoded.length != 32) {
            throw new IllegalStateException("APP_ADMIN_SECRET must be 32 bytes (64 hex characters)");
        }
        locked = true;
        secretBytes = decoded;
        secretHex = raw.trim().toLowerCase();
    }

    public boolean isLocked() {
        return locked;
    }

    public String secretHex() {
        return secretHex;
    }

    public boolean matchesSecret(String hex) {
        if (!locked || hex == null) {
            return false;
        }
        byte[] candidate = decodeHex(hex.trim());
        return candidate.length == secretBytes.length && constantTimeEquals(candidate, secretBytes);
    }

    public boolean matchesCookie(String value) {
        if (!locked) {
            return true;
        }
        if (value == null || value.isBlank()) {
            return false;
        }
        return matchesSecret(value);
    }

    private static byte[] decodeHex(String hex) {
        if (hex.length() % 2 != 0) {
            return new byte[0];
        }
        try {
            return HexFormat.of().parseHex(hex);
        } catch (IllegalArgumentException e) {
            return new byte[0];
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}
