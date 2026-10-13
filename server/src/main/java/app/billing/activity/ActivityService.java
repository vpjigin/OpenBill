package app.billing.activity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import app.billing.auth.CurrentUser;
import app.billing.channel.Channel;
import app.billing.common.UuidV7;
import jakarta.servlet.http.HttpServletRequest;

@Service
public class ActivityService {

    private static final Logger log = LoggerFactory.getLogger(ActivityService.class);
    private static final ZoneId ZONE = ZoneId.systemDefault();

    private final JdbcTemplate jdbc;

    public ActivityService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public record ActivityEntry(UUID id, Instant createdAt, UUID userId, String userName, Channel channel, String ip,
                                String action, String target, String details) {
    }

    /** Best-effort audit row; never propagates errors to callers. */
    public void log(String action, String target, String details) {
        UUID userId = null;
        String userName = null;
        Channel channel = null;
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null && auth.getPrincipal() instanceof CurrentUser cu) {
                userId = cu.userId();
                userName = cu.name();
                channel = cu.channel();
            }
        } catch (Exception ignored) {
            /* no security context */
        }
        String ip = currentRequestIp();
        insert(action, target, details, userId, userName, channel, ip);
    }

    public void log(String action, String target, String details, UUID userId, String userName, Channel channel,
                    String ip) {
        insert(action, target, details, userId, userName, channel, ip);
    }

    public void logLoginFailed(String username, Channel channel, String ip) {
        insert(ActivityActions.LOGIN_FAILED, username, null, null, null, channel, ip);
    }

    @Transactional(readOnly = true)
    public List<ActivityEntry> search(LocalDate from, LocalDate to, String action, String userQuery, int limit) {
        Instant fromInstant = from == null ? null : from.atStartOfDay(ZONE).toInstant();
        Instant toExclusive = to == null ? null : to.plusDays(1).atStartOfDay(ZONE).toInstant();
        String actionFilter = action == null || action.isBlank() ? null : action.trim();
        String user = userQuery == null || userQuery.isBlank() ? null : userQuery.trim();
        int cap = Math.min(Math.max(limit, 1), 500);
        StringBuilder sql = new StringBuilder("""
                SELECT id, created_at, user_id, user_name, channel, ip, action, target, details
                FROM activity_log
                WHERE 1=1
                """);
        List<Object> args = new ArrayList<>();
        if (fromInstant != null) {
            sql.append(" AND created_at >= ?");
            args.add(fromInstant.toEpochMilli());
        }
        if (toExclusive != null) {
            sql.append(" AND created_at < ?");
            args.add(toExclusive.toEpochMilli());
        }
        if (actionFilter != null) {
            sql.append(" AND action = ?");
            args.add(actionFilter);
        }
        if (user != null) {
            sql.append(" AND (lower(user_name) LIKE lower(?) OR lower(target) LIKE lower(?))");
            String like = "%" + user + "%";
            args.add(like);
            args.add(like);
        }
        sql.append(" ORDER BY created_at DESC LIMIT ?");
        args.add(cap);
        return jdbc.query(sql.toString(), (rs, rowNum) -> new ActivityEntry(
                UUID.fromString(rs.getString("id")),
                Instant.ofEpochMilli(rs.getLong("created_at")),
                rs.getString("user_id") == null ? null : UUID.fromString(rs.getString("user_id")),
                rs.getString("user_name"),
                rs.getString("channel") == null ? null : Channel.valueOf(rs.getString("channel")),
                rs.getString("ip"),
                rs.getString("action"),
                rs.getString("target"),
                rs.getString("details")), args.toArray());
    }

    /** Drop rows older than one year (daily). */
    @Scheduled(cron = "0 10 0 * * *")
    @Transactional
    public void purgeOld() {
        Instant cutoff = Instant.now().minus(365, ChronoUnit.DAYS);
        jdbc.update("DELETE FROM activity_log WHERE created_at < ?", cutoff.toEpochMilli());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onServerStarted() {
        log(ActivityActions.SERVER_STARTED, null, null);
    }

    private void insert(String action, String target, String details, UUID userId, String userName, Channel channel,
                        String ip) {
        try {
            Instant now = Instant.now();
            jdbc.update("""
                    INSERT INTO activity_log (id, created_at, user_id, user_name, channel, ip, action, target, details)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, UuidV7.generate().toString(), now.toEpochMilli(),
                    userId == null ? null : userId.toString(),
                    userName, channel == null ? null : channel.name(), ip, action, target, details);
        } catch (Exception e) {
            log.warn("Could not write activity log ({}): {}", action, e.getMessage());
        }
    }

    private static String currentRequestIp() {
        try {
            ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return null;
            }
            HttpServletRequest req = attrs.getRequest();
            return req == null ? null : req.getRemoteAddr();
        } catch (Exception e) {
            return null;
        }
    }
}
