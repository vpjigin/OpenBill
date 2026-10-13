package app.billing.backup;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import app.billing.common.ApiException;

final class BackupValidator {

    private static final byte[] SQLITE_MAGIC = "SQLite format 3\u0000".getBytes();

    private BackupValidator() {
    }

    static void validateSqliteBackup(Path file) {
        if (!Files.isRegularFile(file)) {
            throw ApiException.badRequest("Not a file");
        }
        try {
            byte[] head = Files.readAllBytes(file);
            if (head.length < SQLITE_MAGIC.length) {
                throw ApiException.badRequest("Not a SQLite database");
            }
            for (int i = 0; i < SQLITE_MAGIC.length; i++) {
                if (head[i] != SQLITE_MAGIC[i]) {
                    throw ApiException.badRequest("Not a SQLite database");
                }
            }
        } catch (IOException e) {
            throw ApiException.badRequest("Could not read upload");
        }

        String url = "jdbc:sqlite:" + file.toAbsolutePath() + "?mode=ro";
        try (Connection c = DriverManager.getConnection(url);
             Statement st = c.createStatement();
             ResultSet rs = st.executeQuery("""
                     SELECT name FROM sqlite_master
                     WHERE type='table' AND name IN ('invoice', 'flyway_schema_history')
                     """)) {
            Set<String> found = new HashSet<>();
            while (rs.next()) {
                found.add(rs.getString(1));
            }
            if (!found.contains("invoice") || !found.contains("flyway_schema_history")) {
                throw ApiException.badRequest("Database is missing required tables");
            }
        } catch (ApiException e) {
            throw e;
        } catch (Exception e) {
            throw ApiException.badRequest("Could not open database: " + e.getMessage());
        }
    }
}
