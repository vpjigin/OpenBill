package app.billing.auth;

import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import app.billing.channel.Channel;
import app.billing.common.ApiException;
import app.billing.user.Role;

/** The authenticated principal for a request. */
public record CurrentUser(UUID userId, UUID tokenId, String name, String username, Role role, Channel channel) {

    public static CurrentUser get() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof CurrentUser cu) {
            return cu;
        }
        throw ApiException.unauthorized("Please sign in");
    }
}
