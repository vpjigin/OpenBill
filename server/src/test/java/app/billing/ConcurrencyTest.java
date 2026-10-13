package app.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"app.admin-port=0", "app.billing-port=0", "app.public-port=0"})
@AutoConfigureMockMvc
class ConcurrencyTest {

    @DynamicPropertySource
    static void dataDir(DynamicPropertyRegistry r) throws Exception {
        Path dir = Files.createTempDirectory("billing-concurrency");
        Files.createDirectories(dir.resolve("logs"));
        r.add("app.data-dir", dir::toString);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    @Autowired
    JdbcTemplate jdbc;

    static RequestPostProcessor asAdmin() {
        return req -> {
            req.setLocalPort(0);
            return req;
        };
    }

    @Test
    void parallelAuthAndInvoicesWithoutSqliteBusy() throws Exception {
        mvc.perform(post("/api/setup").with(asAdmin()).contentType(MediaType.APPLICATION_JSON).content("""
                {"shopName":"Busy Shop","gstin":"","stateCode":"32",
                 "adminName":"Owner","username":"owner","password":"secret1"}"""))
                .andExpect(status().isOk());

        String adminToken = login("owner", "secret1", true);
        String billingToken = login("owner", "secret1", false);

        JsonNode item = body(mvc.perform(auth(post("/api/items"), adminToken).with(asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Widget","hsn":"8471","ratePaise":10000,"gstRateBp":1800,"stockMilli":500000}"""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String itemId = item.get("id").asText();

        long stale = Instant.now().minus(2, ChronoUnit.MINUTES).toEpochMilli();
        jdbc.update("UPDATE auth_token SET last_seen_at = ?", stale);

        List<Callable<String>> tasks = new ArrayList<>();
        for (int i = 0; i < 15; i++) {
            tasks.add(() -> performAndCapture(auth(get("/api/admin/summary"), adminToken).with(asAdmin())));
            tasks.add(() -> performAndCapture(auth(get("/api/items"), billingToken)));
        }
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> performAndCapture(auth(post("/api/invoices"), billingToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("""
                            {"lines":[{"itemId":"%s","qtyMilli":1000}],"paymentMode":"CASH"}"""
                            .formatted(itemId))));
        }

        try (ExecutorService pool = Executors.newFixedThreadPool(12)) {
            List<Future<String>> results = pool.invokeAll(tasks);
            List<Integer> seqs = Collections.synchronizedList(new ArrayList<>());
            for (Future<String> f : results) {
                String body = f.get();
                assertThat(body).doesNotContain("SQLITE_BUSY");
                JsonNode node = json.readTree(body);
                if (node.has("invoiceNo")) {
                    String no = node.get("invoiceNo").asText();
                    int slash = no.lastIndexOf('/');
                    assertThat(slash).isGreaterThan(0);
                    seqs.add(Integer.parseInt(no.substring(slash + 1)));
                }
            }
            assertThat(seqs).hasSize(5);
            Collections.sort(seqs);
            assertThat(seqs).containsExactly(1, 2, 3, 4, 5);
        }
    }

    private String performAndCapture(MockHttpServletRequestBuilder req) throws Exception {
        var result = mvc.perform(req).andReturn();
        int status = result.getResponse().getStatus();
        String content = result.getResponse().getContentAsString();
        assertThat(status).isBetween(200, 299);
        assertThat(content).doesNotContain("SQLITE_BUSY");
        return content;
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
