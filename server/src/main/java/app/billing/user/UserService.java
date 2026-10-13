package app.billing.user;

import java.util.List;
import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.billing.activity.ActivityActions;
import app.billing.activity.ActivityService;
import app.billing.auth.AuthService;
import app.billing.common.ApiException;

@Service
public class UserService {

    private final UserRepository repo;
    private final PasswordEncoder encoder;
    private final AuthService auth;
    private final ActivityService activity;

    public UserService(UserRepository repo, PasswordEncoder encoder, AuthService auth, ActivityService activity) {
        this.repo = repo;
        this.encoder = encoder;
        this.auth = auth;
        this.activity = activity;
    }

    @Transactional(readOnly = true)
    public List<User> list() {
        return repo.findByDeletedAtIsNullOrderByCreatedAtAsc();
    }

    @Transactional(readOnly = true)
    public User get(UUID id) {
        return repo.findById(id).filter(u -> !u.isDeleted())
                .orElseThrow(() -> ApiException.notFound("User not found"));
    }

    @Transactional
    public User create(String name, String username, String password, Role role, boolean webAccess) {
        String uname = username.trim();
        if (repo.existsByUsernameIgnoreCaseAndDeletedAtIsNull(uname)) {
            throw ApiException.conflict("Username already taken");
        }
        validatePassword(password);
        User u = new User();
        u.setName(name.trim());
        u.setUsername(uname);
        u.setPasswordHash(encoder.encode(password));
        u.setRole(role);
        u.setWebAccess(webAccess);
        return repo.save(u);
    }

    @Transactional
    public User update(UUID id, String name, Role role, boolean webAccess, boolean active, String newPassword) {
        User u = get(id);
        boolean losingAdmin = u.getRole() == Role.ADMIN && (role != Role.ADMIN || !active);
        if (losingAdmin && repo.countByRoleAndActiveTrueAndDeletedAtIsNull(Role.ADMIN) <= 1) {
            throw ApiException.conflict("Keep at least one active admin");
        }
        u.setName(name.trim());
        u.setRole(role);
        u.setWebAccess(webAccess);
        u.setActive(active);
        if (newPassword != null && !newPassword.isBlank()) {
            validatePassword(newPassword);
            u.setPasswordHash(encoder.encode(newPassword));
        }
        return u;
    }

    @Transactional
    public void changePassword(UUID userId, UUID keepTokenId, String currentPassword, String newPassword) {
        User u = get(userId);
        if (!encoder.matches(currentPassword, u.getPasswordHash())) {
            throw ApiException.badRequest("Current password is wrong");
        }
        validatePassword(newPassword);
        u.setPasswordHash(encoder.encode(newPassword));
        auth.revokeOtherSessions(userId, keepTokenId);
        activity.log(ActivityActions.PASSWORD_CHANGED, u.getUsername(), null);
    }

    @Transactional
    public void resetPassword(UUID userId, String newPassword) {
        User u = get(userId);
        validatePassword(newPassword);
        u.setPasswordHash(encoder.encode(newPassword));
    }

    private static void validatePassword(String password) {
        if (password == null || password.length() < 4) {
            throw ApiException.badRequest("Password or PIN must be at least 4 characters");
        }
    }
}
