package app.billing.auth;

import java.time.Instant;

/** Result of a successful bearer-token lookup (no writes). */
public record AuthenticatedSession(CurrentUser user, Instant lastSeenAt) {
}
