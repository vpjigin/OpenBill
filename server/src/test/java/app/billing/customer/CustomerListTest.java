package app.billing.customer;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"app.admin-port=0", "app.billing-port=0", "app.public-port=0"})
@AutoConfigureMockMvc
class CustomerListTest {

    @DynamicPropertySource
    static void dataDir(DynamicPropertyRegistry r) throws Exception {
        Path dir = Files.createTempDirectory("billing-customer-list");
        Files.createDirectories(dir.resolve("logs"));
        r.add("app.data-dir", dir::toString);
    }

    @Autowired
    MockMvc mvc;

    @Autowired
    ObjectMapper json;

    static RequestPostProcessor asAdmin() {
        return req -> {
            req.setLocalPort(0);
            return req;
        };
    }

    @Test
    void listSearchAndPaging() throws Exception {
        mvc.perform(post("/api/setup").with(asAdmin()).contentType(MediaType.APPLICATION_JSON).content("""
                {"shopName":"Test Shop","gstin":"","stateCode":"32",
                 "adminName":"Owner","username":"owner","password":"secret1"}"""))
                .andExpect(status().isOk());

        String billingToken = login("owner", "secret1", false);

        mvc.perform(auth(post("/api/customers"), billingToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Zulu Corp\"}"))
                .andExpect(status().isOk());
        mvc.perform(auth(post("/api/customers"), billingToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Alpha Traders\"}"))
                .andExpect(status().isOk());

        mvc.perform(auth(get("/api/customers?page=0&size=50"), billingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].name").value("Alpha Traders"))
                .andExpect(jsonPath("$.items[1].name").value("Zulu Corp"));

        mvc.perform(auth(get("/api/customers?q=Zulu"), billingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Zulu Corp"));

        mvc.perform(auth(get("/api/customers?page=0&size=1"), billingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("Alpha Traders"));

        mvc.perform(auth(get("/api/customers?page=1&size=1"), billingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("Zulu Corp"));
    }

    private String login(String user, String pass, boolean admin) throws Exception {
        MockHttpServletRequestBuilder req = post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"%s\",\"password\":\"%s\"}".formatted(user, pass));
        if (admin) {
            req = req.with(asAdmin());
        }
        String res = mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return json.readTree(res).get("token").asText();
    }

    private static MockHttpServletRequestBuilder auth(MockHttpServletRequestBuilder b, String token) {
        return b.header("Authorization", "Bearer " + token);
    }
}
