package app.billing.admin;

import java.io.IOException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import app.billing.activity.ActivityActions;
import app.billing.activity.ActivityService;
import app.billing.auth.AuthService;
import app.billing.auth.AuthToken;
import app.billing.channel.AccessService;
import app.billing.channel.Channel;
import app.billing.channel.TunnelService;
import app.billing.backup.BackupService;
import app.billing.common.ApiException;
import app.billing.config.AppProperties;
import app.billing.invoice.InvoiceRepository;
import app.billing.settings.SettingsService;
import app.billing.settings.ShopSettings;
import app.billing.shop.ShopLogoService;
import app.billing.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** Everything here is ADMIN-only and reachable only on the admin port (127.0.0.1). */
@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AccessService access;
    private final TunnelService tunnel;
    private final AuthService auth;
    private final SettingsService settings;
    private final InvoiceRepository invoices;
    private final UserService users;
    private final AppProperties props;
    private final ActivityService activity;
    private final AttentionService attention;
    private final BackupService backups;
    private final ShopLogoService logos;

    public AdminController(AccessService access, TunnelService tunnel, AuthService auth, SettingsService settings,
                           InvoiceRepository invoices, UserService users, AppProperties props,
                           ActivityService activity, AttentionService attention, BackupService backups,
                           ShopLogoService logos) {
        this.access = access;
        this.tunnel = tunnel;
        this.auth = auth;
        this.settings = settings;
        this.invoices = invoices;
        this.users = users;
        this.props = props;
        this.activity = activity;
        this.attention = attention;
        this.backups = backups;
        this.logos = logos;
    }

    // ---- Access channels ---------------------------------------------------------------------

    public record ChannelStatus(boolean running, String state, List<String> urls, String error, long devices,
                                String linkKind) {
    }

    public record AccessStatus(ChannelStatus local, ChannelStatus lan, ChannelStatus pub) {
    }

    @GetMapping("/access")
    public AccessStatus accessStatus() {
        List<AuthToken> sessions = auth.activeSessions();
        String localUrl = "http://127.0.0.1:" + props.billingPort();

        ChannelStatus local = new ChannelStatus(true, "RUNNING", List.of(localUrl), null,
                count(sessions, Channel.LOCAL), null);

        List<String> lanUrls = access.lanAddresses().stream()
                .map(ip -> "http://" + ip + ":" + props.billingPort()).toList();
        ChannelStatus lan = new ChannelStatus(access.lanRunning(), access.lanRunning() ? "RUNNING" : "STOPPED",
                lanUrls, null, count(sessions, Channel.LAN), null);

        boolean pubOn = access.publicRunning();
        String state = pubOn ? tunnel.state().name() : "STOPPED";
        List<String> pubUrls = pubOn && tunnel.url() != null ? List.of(tunnel.url()) : List.of();
        ShopSettings shop = settings.find().orElse(null);
        boolean namedTunnel = shop != null && shop.getTunnelToken() != null && !shop.getTunnelToken().isBlank();
        String linkKind = pubOn ? (namedTunnel ? "FIXED" : "TEMPORARY") : null;
        ChannelStatus pub = new ChannelStatus(pubOn, state, pubUrls, tunnel.error(), count(sessions, Channel.PUBLIC),
                linkKind);

        return new AccessStatus(local, lan, pub);
    }

    @PostMapping("/access/lan/start")
    public AccessStatus startLan() {
        access.startLan();
        activity.log(ActivityActions.LAN_STARTED, null, null);
        return accessStatus();
    }

    @PostMapping("/access/lan/stop")
    public AccessStatus stopLan() {
        access.stopLan();
        activity.log(ActivityActions.LAN_STOPPED, null, null);
        return accessStatus();
    }

    @PostMapping("/access/public/start")
    public AccessStatus startPublic() {
        access.startPublic();
        activity.log(ActivityActions.PUBLIC_STARTED, null, null);
        return accessStatus();
    }

    @PostMapping("/access/public/stop")
    public AccessStatus stopPublic() {
        access.stopPublic();
        activity.log(ActivityActions.PUBLIC_STOPPED, null, null);
        return accessStatus();
    }

    // ---- Devices (signed-in sessions) --------------------------------------------------------

    public record DeviceDto(UUID id, String userName, String role, Channel channel, String ip, String userAgent,
                            Instant since, Instant lastSeen) {
    }

    @GetMapping("/devices")
    public List<DeviceDto> devices() {
        return auth.activeSessions().stream()
                .filter(t -> t.getChannel() != Channel.ADMIN)
                .map(t -> new DeviceDto(t.getId(), t.getUser().getName(), t.getUser().getRole().name(),
                        t.getChannel(), t.getIp(), t.getUserAgent(), t.getCreatedAt(), t.getLastSeenAt()))
                .toList();
    }

    @DeleteMapping("/devices/{id}")
    public void signOutDevice(@PathVariable UUID id) {
        auth.activeSessions().stream().filter(t -> t.getId().equals(id)).findFirst().ifPresent(t -> activity.log(
                ActivityActions.DEVICE_SIGNED_OUT, t.getUser().getUsername(), t.getChannel().name()));
        auth.revoke(id);
    }

    @PostMapping("/users/{id}/sign-out")
    public void signOutUser(@PathVariable UUID id) {
        users.get(id);
        auth.revokeUser(id);
    }

    // ---- Dashboard ---------------------------------------------------------------------------

    public record Summary(String shopName, long billsToday, long salesTodayPaise, long devicesOnline,
                          Instant lastBackupAt) {
    }

    @GetMapping("/attention")
    public List<AttentionService.AttentionItem> attention() {
        return attention.list();
    }

    @GetMapping("/summary")
    public Summary summary() {
        Instant startOfDay = LocalDate.now(ZoneId.systemDefault()).atStartOfDay(ZoneId.systemDefault()).toInstant();
        long devices = auth.activeSessions().stream().filter(t -> t.getChannel() != Channel.ADMIN).count();
        return new Summary(settings.get().getShopName(), invoices.countSince(startOfDay),
                invoices.totalSince(startOfDay), devices, backups.lastBackupAt());
    }

    // ---- Shop settings -----------------------------------------------------------------------

    public record SettingsDto(String shopName, String gstin, String stateCode, String address, String phone,
                              String pincode, String invoicePrefix, boolean tunnelTokenSet, String publicUrl,
                              boolean hasLogo, boolean receiptShowLogo) {
        static SettingsDto of(ShopSettings s, boolean hasLogo) {
            return new SettingsDto(s.getShopName(), s.getGstin(), s.getStateCode(), s.getAddress(), s.getPhone(),
                    s.getPincode(), s.getInvoicePrefix(), s.getTunnelToken() != null && !s.getTunnelToken().isBlank(),
                    s.getPublicUrl(), hasLogo, s.isReceiptShowLogo());
        }
    }

    public record SettingsInput(@NotBlank String shopName, String gstin, String stateCode, String address,
                                String phone, String pincode,
                                @NotBlank @Pattern(regexp = "^[A-Z0-9-]{1,8}$", message = "1-8 capital letters, digits or -")
                                String invoicePrefix,
                                /* null = keep, "" = clear */ String tunnelToken, String publicUrl,
                                boolean receiptShowLogo) {
    }

    @GetMapping("/settings")
    public SettingsDto getSettings() {
        ShopSettings s = settings.get();
        return SettingsDto.of(s, logos.hasLogo());
    }

    @PutMapping("/settings")
    public SettingsDto saveSettings(@Valid @RequestBody SettingsInput in) {
        SettingsDto saved = SettingsDto.of(settings.update(s -> {
            s.setShopName(in.shopName().trim());
            s.setGstin(blankToNull(in.gstin(), true));
            s.setStateCode(blankToNull(in.stateCode(), true));
            s.setAddress(blankToNull(in.address(), false));
            s.setPhone(blankToNull(in.phone(), false));
            String pin = blankToNull(in.pincode(), true);
            if (pin != null && !pin.matches("\\d{6}")) {
                throw ApiException.badRequest("Pincode must be 6 digits");
            }
            s.setPincode(pin);
            s.setInvoicePrefix(in.invoicePrefix());
            if (in.tunnelToken() != null) {
                s.setTunnelToken(blankToNull(in.tunnelToken(), false));
            }
            s.setPublicUrl(blankToNull(in.publicUrl(), false));
            s.setReceiptShowLogo(in.receiptShowLogo());
        }), logos.hasLogo());
        activity.log(ActivityActions.SETTINGS_UPDATED, saved.shopName(), null);
        return saved;
    }

    @PostMapping("/settings/logo")
    public SettingsDto uploadLogo(@RequestPart("file") MultipartFile file) {
        logos.upload(file);
        ShopSettings s = settings.get();
        activity.log(ActivityActions.SETTINGS_UPDATED, s.getShopName(), "logo uploaded");
        return SettingsDto.of(s, true);
    }

    @DeleteMapping("/settings/logo")
    public SettingsDto removeLogo() {
        logos.remove();
        ShopSettings s = settings.get();
        activity.log(ActivityActions.SETTINGS_UPDATED, s.getShopName(), "logo removed");
        return SettingsDto.of(s, false);
    }

    @GetMapping("/settings/logo")
    public ResponseEntity<byte[]> settingsLogo() {
        ShopLogoService.LogoPayload logo = logos.read();
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noStore())
                .header(HttpHeaders.CONTENT_TYPE, logo.mime())
                .body(logo.bytes());
    }

    // ---- Open the billing website in the default browser -------------------------------------

    public record OpenRequest(String url) {
    }

    /**
     * Opens the billing website (or one of its current Wi-Fi/public links) in the default browser.
     * Only URLs the server itself is serving are accepted, never arbitrary ones.
     */
    @PostMapping("/open-billing")
    public Map<String, String> openBilling(@RequestBody(required = false) OpenRequest body) {
        String localUrl = "http://127.0.0.1:" + props.billingPort();
        String url = body == null || body.url() == null || body.url().isBlank() ? localUrl : body.url().trim();
        AccessStatus st = accessStatus();
        boolean known = url.equals(localUrl) || st.lan().urls().contains(url) || st.pub().urls().contains(url);
        if (!known) {
            throw ApiException.badRequest("Unknown link");
        }
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        List<String> cmd = os.contains("win") ? List.of("rundll32", "url.dll,FileProtocolHandler", url)
                : os.contains("mac") ? List.of("open", url)
                : List.of("xdg-open", url);
        try {
            new ProcessBuilder(cmd).start();
        } catch (IOException e) {
            throw new ApiException(HttpStatus.INTERNAL_SERVER_ERROR, "Could not open the browser. Open " + url);
        }
        return Map.of("url", url);
    }

    private static long count(List<AuthToken> sessions, Channel c) {
        return sessions.stream().filter(t -> t.getChannel() == c).count();
    }

    private static String blankToNull(String v, boolean upper) {
        if (v == null || v.isBlank()) {
            return null;
        }
        return upper ? v.trim().toUpperCase(Locale.ROOT) : v.trim();
    }
}
