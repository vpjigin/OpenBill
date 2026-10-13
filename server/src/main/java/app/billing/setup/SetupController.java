package app.billing.setup;

import java.util.Map;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.billing.activity.ActivityActions;
import app.billing.activity.ActivityService;
import app.billing.common.ApiException;
import app.billing.settings.SettingsService;
import app.billing.settings.ShopSettings;
import app.billing.user.Role;
import app.billing.user.UserRepository;
import app.billing.user.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/** First run, from the desktop admin app only (ChannelFilter blocks /api/setup elsewhere). */
@RestController
@RequestMapping("/api/setup")
public class SetupController {

    private final UserRepository userRepo;
    private final UserService users;
    private final SettingsService settings;
    private final ActivityService activity;

    public SetupController(UserRepository userRepo, UserService users, SettingsService settings,
                           ActivityService activity) {
        this.userRepo = userRepo;
        this.users = users;
        this.settings = settings;
        this.activity = activity;
    }

    public record SetupRequest(@NotBlank String shopName, String gstin, String stateCode,
                               @NotBlank String adminName, @NotBlank String username, @NotBlank String password) {
    }

    @GetMapping("/status")
    public Map<String, Boolean> status() {
        return Map.of("setupDone", userRepo.countByDeletedAtIsNull() > 0);
    }

    @PostMapping
    @Transactional
    public Map<String, Boolean> setup(@Valid @RequestBody SetupRequest body) {
        if (userRepo.countByDeletedAtIsNull() > 0) {
            throw ApiException.conflict("Already set up");
        }
        ShopSettings s = new ShopSettings();
        s.setShopName(body.shopName().trim());
        s.setGstin(blankToNull(body.gstin()));
        s.setStateCode(blankToNull(body.stateCode()));
        settings.create(s);
        users.create(body.adminName(), body.username(), body.password(), Role.ADMIN, true);
        activity.log(ActivityActions.SETUP, body.shopName().trim(), body.username());
        return Map.of("setupDone", true);
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim().toUpperCase();
    }
}
