package app.billing.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import jakarta.servlet.http.Cookie;

@SpringBootTest(properties = {"app.admin-port=0", "app.billing-port=0", "app.public-port=0"})
@AutoConfigureMockMvc
class AdminDesktopLockTest {

    /** 32-byte test secret (64 hex chars). */
    static final String SECRET_HEX = "0123456789abcdef".repeat(4);

    @DynamicPropertySource
    static void dataDir(DynamicPropertyRegistry r) throws Exception {
        Path dir = Files.createTempDirectory("billing-admin-lock");
        Files.createDirectories(dir.resolve("logs"));
        r.add("app.data-dir", dir::toString);
        r.add("app.admin-secret", () -> SECRET_HEX);
    }

    @Autowired
    MockMvc mvc;

    static RequestPostProcessor asAdmin() {
        return req -> {
            req.setLocalPort(0);
            return req;
        };
    }

    static RequestPostProcessor asBilling() {
        return req -> {
            req.setLocalPort(1);
            return req;
        };
    }

    @Test
    void adminApiWithoutCookieIsForbidden() throws Exception {
        mvc.perform(get("/api/admin/summary").with(asAdmin())).andExpect(status().isForbidden());
    }

    @Test
    void adminHealthWithoutCookieIsForbidden() throws Exception {
        mvc.perform(get("/api/health").with(asAdmin())).andExpect(status().isForbidden());
    }

    @Test
    void desktopWrongSecretIsForbidden() throws Exception {
        mvc.perform(get("/__desktop").param("secret", "deadbeef").with(asAdmin()))
                .andExpect(status().isForbidden());
    }

    @Test
    void desktopCorrectSecretSetsCookieAndAllowsAdminApi() throws Exception {
        var result = mvc.perform(get("/__desktop").param("secret", SECRET_HEX).with(asAdmin()))
                .andExpect(status().is3xxRedirection())
                .andExpect(header().string("Location", "/"))
                .andExpect(cookie().exists(AdminDesktopAuth.COOKIE_NAME))
                .andReturn();

        Cookie cookie = result.getResponse().getCookie(AdminDesktopAuth.COOKIE_NAME);
        assertThat(cookie).isNotNull();
        assertThat(cookie.getValue()).isEqualTo(SECRET_HEX);

        mvc.perform(get("/api/admin/summary").with(asAdmin()).cookie(cookie))
                .andExpect(status().isUnauthorized());

        mvc.perform(post("/api/setup").with(asAdmin()).cookie(cookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"shopName":"Lock Shop","adminName":"Owner","username":"owner","password":"secret1"}"""))
                .andExpect(status().isOk());
    }

    @Test
    void billingPortUnaffected() throws Exception {
        mvc.perform(get("/api/health").with(asBilling())).andExpect(status().isOk());
        mvc.perform(get("/api/admin/summary").with(asBilling())).andExpect(status().isNotFound());
    }
}
