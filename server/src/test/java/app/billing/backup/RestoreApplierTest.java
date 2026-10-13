package app.billing.backup;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RestoreApplierTest {

    @TempDir
    Path dataDir;

    @Test
    void appliesPendingRestoreAndKeepsSafetyCopy() throws Exception {
        Path backups = dataDir.resolve("backups");
        Files.createDirectories(backups);

        Path live = dataDir.resolve("billing.db");
        createDb(live, "live");

        Path snapshot = backups.resolve("billing-20260101-120000.db");
        createDb(snapshot, "snapshot");

        Files.writeString(dataDir.resolve(RestoreApplier.PENDING_FILE), snapshot.getFileName().toString());

        assertThat(RestoreApplier.applyIfPending(dataDir)).isTrue();
        assertThat(Files.exists(dataDir.resolve(RestoreApplier.PENDING_FILE))).isFalse();
        assertThat(readMarker(live)).isEqualTo("snapshot");

        List<Path> preRestore;
        try (Stream<Path> stream = Files.list(backups)) {
            preRestore = stream.filter(p -> p.getFileName().toString().startsWith("pre-restore-")).toList();
        }
        assertThat(preRestore).hasSize(1);
        assertThat(readMarker(preRestore.getFirst())).isEqualTo("live");
    }

    private static void createDb(Path file, String label) throws Exception {
        Files.deleteIfExists(file);
        String url = "jdbc:sqlite:" + file.toAbsolutePath();
        try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement()) {
            st.execute("CREATE TABLE marker (label TEXT)");
            st.execute("INSERT INTO marker VALUES ('" + label + "')");
        }
    }

    private static String readMarker(Path db) throws Exception {
        String url = "jdbc:sqlite:" + db.toAbsolutePath();
        try (Connection c = DriverManager.getConnection(url); Statement st = c.createStatement();
             var rs = st.executeQuery("SELECT label FROM marker")) {
            rs.next();
            return rs.getString(1);
        }
    }
}
