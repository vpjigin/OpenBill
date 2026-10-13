package app.billing.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import app.billing.invoice.InvoiceService;

@SpringBootTest(properties = {"app.admin-port=0", "app.billing-port=0", "app.public-port=0"})
@AutoConfigureMockMvc
class MigrationUpgradeTest {

    static final String USER_ID = "018f0000-0000-7000-8000-000000000001";
    static final String SHOP_ID = "018f0000-0000-7000-8000-000000000002";
    static final String ITEM_A = "018f0000-0000-7000-8000-000000000010";
    static final String ITEM_B = "018f0000-0000-7000-8000-000000000011";
    static final String INV1 = "018f0000-0000-7000-8000-000000000020";
    static final String INV2 = "018f0000-0000-7000-8000-000000000021";
    static final String LINE1 = "018f0000-0000-7000-8000-000000000030";
    static final String LINE2 = "018f0000-0000-7000-8000-000000000031";
    static final long TS = 1_700_000_000_000L;

    static final Path UPGRADE_DATA_DIR;
    static final Map<String, Long> ROW_COUNTS_AFTER_V1_SEED = new LinkedHashMap<>();

    static {
        try {
            UPGRADE_DATA_DIR = Files.createTempDirectory("billing-migration-upgrade");
            Files.createDirectories(UPGRADE_DATA_DIR.resolve("logs"));
            Path db = UPGRADE_DATA_DIR.resolve("billing.db");
            migrateTo(db, "1");
            seedV1Database(db);
            ROW_COUNTS_AFTER_V1_SEED.putAll(countRows(db));
            migrateTo(db, null);
            assertUpgradedData(db);
        } catch (Exception e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void dataDir(DynamicPropertyRegistry r) {
        r.add("app.data-dir", () -> UPGRADE_DATA_DIR.toString());
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Test
    void freshEmptyDatabaseMigratesToLatest() throws Exception {
        Path dir = Files.createTempDirectory("billing-migration-fresh");
        Path db = dir.resolve("billing.db");
        migrateTo(db, null);
        try (Connection c = open(db)) {
            int versions = queryInt(c, "SELECT COUNT(*) FROM flyway_schema_history WHERE success = 1");
            assertThat(versions).isEqualTo(13);
        }
    }

    @Test
    void rowCountsUnchangedAfterUpgrade() throws Exception {
        Map<String, Long> after = countRows(UPGRADE_DATA_DIR.resolve("billing.db"));
        for (var e : ROW_COUNTS_AFTER_V1_SEED.entrySet()) {
            assertThat(after.get(e.getKey())).as("table %s", e.getKey()).isEqualTo(e.getValue());
        }
    }

    @Test
    void oldInvoicesLoadAndSequenceContinues() throws Exception {
        String fy = InvoiceService.financialYear(LocalDate.now());
        String billing = login("owner", "secret1", false);

        mvc.perform(auth(get("/api/invoices/" + INV1), billing))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].qtyMilli").value(5000));

        mvc.perform(auth(get("/api/invoices/" + INV2), billing))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lines[0].qtyMilli").value(1000));

        JsonNode items = body(mvc.perform(auth(get("/api/items"), billing))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long stockA = 0;
        for (JsonNode row : items.get("items")) {
            if (ITEM_A.equals(row.get("id").asText())) {
                stockA = row.get("stockMilli").asLong();
                break;
            }
        }
        assertThat(stockA).isEqualTo(12_000);

        JsonNode created = body(mvc.perform(auth(post("/api/invoices"), billing)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"lines":[{"itemId":"%s","qtyMilli":1000}],"paymentMode":"CASH"}"""
                        .formatted(ITEM_A)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(created.get("invoiceNo").asText()).isEqualTo("INV/%s/000003".formatted(fy));
    }

    private static void migrateTo(Path db, String targetVersion) {
        var config = Flyway.configure()
                .locations("classpath:db/migration")
                .dataSource(jdbcUrl(db), null, null);
        if (targetVersion != null) {
            config.target(targetVersion);
        }
        config.load().migrate();
    }

    private static String jdbcUrl(Path db) {
        return "jdbc:sqlite:" + db.toAbsolutePath()
                + "?journal_mode=WAL&foreign_keys=on&busy_timeout=5000&synchronous=NORMAL";
    }

    private static Connection open(Path db) throws Exception {
        return DriverManager.getConnection(jdbcUrl(db));
    }

    private static void seedV1Database(Path db) throws Exception {
        String hash = new BCryptPasswordEncoder().encode("secret1");
        String fy = InvoiceService.financialYear(LocalDate.now());
        try (Connection c = open(db); Statement st = c.createStatement()) {
            st.executeUpdate("""
                    INSERT INTO app_user (id, created_at, updated_at, version, name, username, password_hash, role, web_access, active)
                    VALUES ('%s', %d, %d, 0, 'Owner', 'owner', '%s', 'ADMIN', 1, 1)
                    """.formatted(USER_ID, TS, TS, hash));
            st.executeUpdate("""
                    INSERT INTO shop_settings (id, created_at, updated_at, version, shop_name, invoice_prefix, lan_enabled, public_enabled)
                    VALUES ('%s', %d, %d, 0, 'Legacy Shop', 'INV', 0, 0)
                    """.formatted(SHOP_ID, TS, TS));
            st.executeUpdate("""
                    INSERT INTO item (id, created_at, updated_at, version, name, unit, rate_paise, gst_rate_bp, stock_qty, active)
                    VALUES ('%s', %d, %d, 0, 'Item A', 'NOS', 10000, 1800, 12, 1),
                           ('%s', %d, %d, 0, 'Item B', 'KG', 25000, 500, 3, 1)
                    """.formatted(ITEM_A, TS, TS, ITEM_B, TS, TS));
            st.executeUpdate("""
                    INSERT INTO invoice_sequence (fy, last_number) VALUES ('%s', 2)
                    """.formatted(fy));
            st.executeUpdate("""
                    INSERT INTO invoice (id, created_at, updated_at, version, invoice_no, fy, seq, invoice_date,
                        payment_mode, taxable_paise, cgst_paise, sgst_paise, igst_paise, round_off_paise, total_paise,
                        created_by, channel)
                    VALUES ('%s', %d, %d, 0, 'INV/%s/000001', '%s', 1, %d, 'CASH', 10000, 900, 900, 0, 0, 11800, '%s', 'LOCAL'),
                           ('%s', %d, %d, 0, 'INV/%s/000002', '%s', 2, %d, 'CASH', 5000, 125, 125, 0, 0, 5250, '%s', 'LOCAL')
                    """.formatted(
                    INV1, TS, TS, fy, fy, TS, USER_ID,
                    INV2, TS, TS, fy, fy, TS, USER_ID));
            st.executeUpdate("""
                    INSERT INTO invoice_line (id, created_at, updated_at, version, invoice_id, line_no, item_id, name, qty,
                        rate_paise, gst_rate_bp, taxable_paise, cgst_paise, sgst_paise, igst_paise, total_paise)
                    VALUES ('%s', %d, %d, 0, '%s', 1, '%s', 'Item A', 5, 10000, 1800, 10000, 900, 900, 0, 11800),
                           ('%s', %d, %d, 0, '%s', 1, '%s', 'Item B', 1, 25000, 500, 5000, 125, 125, 0, 5250)
                    """.formatted(
                    LINE1, TS, TS, INV1, ITEM_A,
                    LINE2, TS, TS, INV2, ITEM_B));
        }
    }

    private static void assertUpgradedData(Path db) throws Exception {
        try (Connection c = open(db)) {
            assertThat(queryLong(c,
                    "SELECT qty_milli FROM invoice_line WHERE id = '" + LINE1 + "'")).isEqualTo(5_000);
            assertThat(queryLong(c,
                    "SELECT qty_milli FROM invoice_line WHERE id = '" + LINE2 + "'")).isEqualTo(1_000);
            assertThat(queryLong(c,
                    "SELECT stock_milli FROM item WHERE id = '" + ITEM_A + "'")).isEqualTo(12_000);
            assertThat(queryLong(c,
                    "SELECT stock_milli FROM item WHERE id = '" + ITEM_B + "'")).isEqualTo(3_000);
        }
    }

    private static Map<String, Long> countRows(Path db) throws Exception {
        String[] tables = {"app_user", "shop_settings", "item", "invoice", "invoice_line", "invoice_sequence"};
        Map<String, Long> counts = new LinkedHashMap<>();
        try (Connection c = open(db)) {
            for (String t : tables) {
                counts.put(t, queryLong(c, "SELECT COUNT(*) FROM " + t));
            }
        }
        return counts;
    }

    private static long queryLong(Connection c, String sql) throws Exception {
        try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getLong(1);
        }
    }

    private static int queryInt(Connection c, String sql) throws Exception {
        return Math.toIntExact(queryLong(c, sql));
    }

    static RequestPostProcessor asAdmin() {
        return req -> {
            req.setLocalPort(0);
            return req;
        };
    }

    private String login(String user, String pass, boolean admin) throws Exception {
        MockHttpServletRequestBuilder req = post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(user, pass));
        if (admin) {
            req = req.with(asAdmin());
        }
        return json.readTree(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("token").asText();
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token);
    }

    private JsonNode body(String s) throws Exception {
        return json.readTree(s);
    }
}
