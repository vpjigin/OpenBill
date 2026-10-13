package app.billing.user;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.billing.activity.ActivityActions;
import app.billing.activity.ActivityService;
import app.billing.auth.AuthService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** Users are created and managed only from the desktop admin app (admin port). */
@RestController
@RequestMapping("/api/admin/users")
public class AdminUserController {

    private final UserService users;
    private final AuthService auth;
    private final ActivityService activity;

    public AdminUserController(UserService users, AuthService auth, ActivityService activity) {
        this.users = users;
        this.auth = auth;
        this.activity = activity;
    }

    public record CreateUser(@NotBlank String name,
                             @NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{3,32}$",
                                     message = "3-32 letters, digits, . _ -") String username,
                             @NotBlank String password,
                             @NotNull Role role,
                             boolean webAccess) {
    }

    public record UpdateUser(@NotBlank String name, @NotNull Role role, boolean webAccess,
                             boolean active, String newPassword) {
    }

    public record ResetPasswordRequest(@NotBlank String newPassword) {
    }

    @GetMapping
    public List<UserDto> list() {
        return users.list().stream().map(UserDto::of).toList();
    }

    @PostMapping
    public UserDto create(@Valid @RequestBody CreateUser body) {
        User u = users.create(body.name(), body.username(), body.password(), body.role(), body.webAccess());
        activity.log(ActivityActions.USER_CREATED, u.getUsername(), u.getRole().name());
        return UserDto.of(u);
    }

    @PutMapping("/{id}")
    public UserDto update(@PathVariable UUID id, @Valid @RequestBody UpdateUser body) {
        User u = users.update(id, body.name(), body.role(), body.webAccess(), body.active(), body.newPassword());
        activity.log(ActivityActions.USER_UPDATED, u.getUsername(), null);
        if (!u.isActive() || (body.newPassword() != null && !body.newPassword().isBlank())) {
            auth.revokeUser(id);        // disabled or password changed: sign out everywhere
        } else if (!u.isWebAccess()) {
            auth.revokeUserRemote(id);  // web access removed: sign out Wi-Fi and public devices
        }
        return UserDto.of(u);
    }

    @PostMapping("/{id}/reset-password")
    public void resetPassword(@PathVariable UUID id, @Valid @RequestBody ResetPasswordRequest body) {
        User u = users.get(id);
        users.resetPassword(id, body.newPassword());
        activity.log(ActivityActions.PASSWORD_RESET, u.getUsername(), null);
        auth.revokeUser(id);
    }
}
