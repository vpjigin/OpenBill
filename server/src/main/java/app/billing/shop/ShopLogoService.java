package app.billing.shop;

import java.util.Base64;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import app.billing.common.ApiException;
import app.billing.settings.SettingsService;
import app.billing.settings.ShopSettings;

@Service
public class ShopLogoService {

    static final int MAX_BYTES = 200 * 1024;

    private final SettingsService settings;

    public ShopLogoService(SettingsService settings) {
        this.settings = settings;
    }

    public record LogoPayload(byte[] bytes, String mime) {
    }

    @Transactional(readOnly = true)
    public LogoPayload read() {
        ShopSettings s = settings.get();
        if (s.getLogo() == null || s.getLogo().isBlank()) {
            throw ApiException.notFound("No logo uploaded");
        }
        String mime = s.getLogoMime();
        if (mime == null || mime.isBlank()) {
            mime = "image/png";
        }
        byte[] bytes = Base64.getDecoder().decode(s.getLogo().trim());
        return new LogoPayload(bytes, mime);
    }

    @Transactional(readOnly = true)
    public boolean hasLogo() {
        return settings.find()
                .map(s -> s.getLogo() != null && !s.getLogo().isBlank())
                .orElse(false);
    }

    @Transactional
    public void upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw ApiException.badRequest("Choose a PNG or JPEG file");
        }
        if (file.getSize() > MAX_BYTES) {
            throw ApiException.badRequest("Logo must be at most 200 KB");
        }
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (Exception e) {
            throw ApiException.badRequest("Could not read the uploaded file");
        }
        String mime = detectMime(bytes);
        if (mime == null) {
            throw ApiException.badRequest("Logo must be PNG or JPEG");
        }
        String encoded = Base64.getEncoder().encodeToString(bytes);
        settings.update(s -> {
            s.setLogo(encoded);
            s.setLogoMime(mime);
        });
    }

    @Transactional
    public void remove() {
        settings.update(s -> {
            s.setLogo(null);
            s.setLogoMime(null);
        });
    }

    static String detectMime(byte[] data) {
        if (data.length >= 8 && data[0] == (byte) 0x89 && data[1] == 0x50 && data[2] == 0x4E && data[3] == 0x47) {
            return "image/png";
        }
        if (data.length >= 3 && (data[0] & 0xFF) == 0xFF && (data[1] & 0xFF) == 0xD8 && (data[2] & 0xFF) == 0xFF) {
            return "image/jpeg";
        }
        return null;
    }

    static String extensionForMime(String mime) {
        if ("image/jpeg".equalsIgnoreCase(mime)) {
            return "jpg";
        }
        return "png";
    }
}
