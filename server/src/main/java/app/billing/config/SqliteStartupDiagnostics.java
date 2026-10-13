package app.billing.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

@Component
public class SqliteStartupDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(SqliteStartupDiagnostics.class);

    private final JdbcTemplate jdbc;

    public SqliteStartupDiagnostics(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void logConnectionPragmas() {
        Integer busyTimeout = jdbc.queryForObject("PRAGMA busy_timeout", Integer.class);
        String journalMode = jdbc.queryForObject("PRAGMA journal_mode", String.class);
        log.info("SQLite PRAGMA busy_timeout={}, journal_mode={}", busyTimeout, journalMode);
    }
}
