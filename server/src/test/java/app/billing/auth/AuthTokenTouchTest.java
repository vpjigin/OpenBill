package app.billing.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

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

@SpringBootTest(properties = {"app.admin-port=0", "app.billing-port=0", "app.public-port=0"})
@AutoConfigureMockMvc
class AuthTokenTouchTest {

    @DynamicPropertySource
    static void dataDir(DynamicPropertyRegistry r) throws Exception {
        Path dir = Files.createTempDirectory("billing-auth-touch");
        Files.createDirectories(dir.resolve("logs"));
        r.add("app.data-dir", dir::toString);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void sequentialGetsStayFastAfterBackdatedLastSeen() throws Exception {
        setupShop();
        String billingToken = login("owner", "secret1", false);
        UUID tokenId = jdbc.queryForObject("SELECT id FROM auth_token LIMIT 1", UUID.class);
        long stale = Instant.now().minus(2, ChronoUnit.MINUTES).toEpochMilli();
        jdbc.update("UPDATE auth_token SET last_seen_at = ? WHERE id = ?", stale, tokenId.toString());

        for (int i = 0; i < 10; i++) {
            long start = System.nanoTime();
            mvc.perform(auth(get("/api/items"), billingToken)).andExpect(status().isOk());
            assertThat(Duration.ofNanos(System.nanoTime() - start)).isLessThan(Duration.ofMillis(500));
        }

        long seen = jdbc.queryForObject("SELECT last_seen_at FROM auth_token WHERE id = ?", Long.class, tokenId.toString());
        assertThat(seen).isGreaterThan(stale);
    }

    private void setupShop() throws Exception {
        mvc.perform(post("/api/setup").with(asAdmin()).contentType(MediaType.APPLICATION_JSON).content("""
                {"shopName":"Touch Shop","gstin":"","stateCode":"32",
                 "adminName":"Owner","username":"owner","password":"secret1"}"""))
                .andExpect(status().isOk());
    }

    private String login(String user, String pass, boolean admin) throws Exception {
        MockHttpServletRequestBuilder req = post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(user, pass));
        if (admin) {
            req = req.with(asAdmin());
        }
        return new com.fasterxml.jackson.databind.ObjectMapper()
                .readTree(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("token").asText();
    }

    static RequestPostProcessor asAdmin() {
        return req -> {
            req.setLocalPort(0);
            return req;
        };
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token);
    }
}
