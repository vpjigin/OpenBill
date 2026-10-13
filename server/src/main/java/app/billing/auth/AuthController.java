package app.billing.auth;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.billing.activity.ActivityActions;
import app.billing.activity.ActivityService;
import app.billing.channel.Channel;
import app.billing.channel.ChannelFilter;
import app.billing.user.UserService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;
    private final UserService users;
    private final ActivityService activity;

    public AuthController(AuthService auth, UserService users, ActivityService activity) {
        this.auth = auth;
        this.users = users;
        this.activity = activity;
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record ChangePasswordRequest(@NotBlank String currentPassword, @NotBlank String newPassword) {
    }

    @PostMapping("/login")
    public AuthService.LoginResult login(@Valid @RequestBody LoginRequest body, HttpServletRequest req) {
        Channel channel = ChannelFilter.channelOf(req);
        return auth.login(body.username(), body.password(), channel, clientIp(req, channel), req.getHeader("User-Agent"));
    }

    @GetMapping("/me")
    public CurrentUser me() {
        return CurrentUser.get();
    }

    @PostMapping("/logout")
    public void logout() {
        CurrentUser cu = CurrentUser.get();
        activity.log(ActivityActions.LOGOUT, cu.username(), null);
        auth.logout(cu.tokenId());
    }

    @PostMapping("/change-password")
    public void changePassword(@Valid @RequestBody ChangePasswordRequest body) {
        CurrentUser cu = CurrentUser.get();
        users.changePassword(cu.userId(), cu.tokenId(), body.currentPassword(), body.newPassword());
    }

    /** Behind the tunnel every request comes from 127.0.0.1; Cloudflare passes the real IP. */
    static String clientIp(HttpServletRequest req, Channel channel) {
        if (channel == Channel.PUBLIC) {
            String cf = req.getHeader("CF-Connecting-IP");
            if (cf != null && !cf.isBlank()) {
                return cf.trim();
            }
        }
        return req.getRemoteAddr();
    }
}
