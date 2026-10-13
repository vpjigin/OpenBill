package app.billing.user;

import java.time.Instant;
import java.util.UUID;

public record UserDto(UUID id, String name, String username, Role role, boolean webAccess,
                      boolean active, Instant lastLoginAt, long version) {

    public static UserDto of(User u) {
        return new UserDto(u.getId(), u.getName(), u.getUsername(), u.getRole(), u.isWebAccess(),
                u.isActive(), u.getLastLoginAt(), u.getVersion());
    }
}
