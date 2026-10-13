package app.billing.shop;

import java.time.Duration;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.billing.common.GstStates;
import app.billing.config.AppProperties;
import app.billing.settings.SettingsService;
import app.billing.settings.ShopSettings;

@RestController
@RequestMapping("/api/shop")
public class ShopController {

    private final SettingsService settings;
    private final AppProperties app;
    private final ShopLogoService logos;

    public ShopController(SettingsService settings, AppProperties app, ShopLogoService logos) {
        this.settings = settings;
        this.app = app;
        this.logos = logos;
    }

    public record ShopDto(String appName, String shopName, String gstin, String stateCode, String stateName,
                          String address, String phone, boolean hasLogo, boolean receiptShowLogo) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public ShopDto get() {
        ShopSettings s = settings.get();
        String code = s.getStateCode();
        return new ShopDto(app.name(), s.getShopName(), s.getGstin(), code, GstStates.nameOf(code), s.getAddress(),
                s.getPhone(), logos.hasLogo(), s.isReceiptShowLogo());
    }

    @GetMapping("/logo")
    @Transactional(readOnly = true)
    public ResponseEntity<byte[]> logo() {
        ShopLogoService.LogoPayload logo = logos.read();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePublic())
                .header(HttpHeaders.CONTENT_TYPE, logo.mime())
                .body(logo.bytes());
    }
}
