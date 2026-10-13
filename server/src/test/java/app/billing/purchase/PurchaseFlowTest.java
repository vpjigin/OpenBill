package app.billing.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
class PurchaseFlowTest {

    @DynamicPropertySource
    static void dataDir(DynamicPropertyRegistry r) throws Exception {
        Path dir = Files.createTempDirectory("billing-purchase-test");
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
    void purchaseStockCostCancelAndDuplicateBill() throws Exception {
        mvc.perform(post("/api/setup").with(asAdmin()).contentType(MediaType.APPLICATION_JSON).content("""
                {"shopName":"Purchase Shop","gstin":"","stateCode":"32",
                 "adminName":"Owner","username":"owner","password":"secret1"}"""))
                .andExpect(status().isOk());

        String admin = login("owner", "secret1", true);
        String billing = login("owner", "secret1", false);

        JsonNode item = body(mvc.perform(auth(post("/api/items"), admin).with(asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Soap","hsn":"3401","ratePaise":12000,"gstRateBp":1800,"stockMilli":5000}"""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String itemId = item.get("id").asText();
        assertThat(item.get("costPaise").asLong()).isZero();

        JsonNode supplier = body(mvc.perform(auth(post("/api/suppliers"), billing)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Wholesale Co","gstin":"32AAAAA0000A1Z5"}"""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        String today = java.time.LocalDate.now().toString();
        String purchaseBody = """
                {"supplierId":"%s","billNo":"PB-001","billDate":"%s","notes":"test",
                 "lines":[{"itemId":"%s","qtyMilli":10000,"costPaise":8000,"gstRateBp":1800}]}"""
                .formatted(supplier.get("id").asText(), today, itemId);

        JsonNode purchase = body(mvc.perform(auth(post("/api/purchases"), billing)
                .contentType(MediaType.APPLICATION_JSON).content(purchaseBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(purchase.get("totalPaise").asLong()).isGreaterThan(0);

        JsonNode itemAfter = body(mvc.perform(auth(get("/api/items"), billing))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long stock = 0;
        long cost = 0;
        for (JsonNode row : itemAfter) {
            if (itemId.equals(row.get("id").asText())) {
                stock = row.get("stockMilli").asLong();
                cost = row.get("costPaise").asLong();
                break;
            }
        }
        assertThat(stock).isEqualTo(15_000);
        assertThat(cost).isEqualTo(8_000);

        mvc.perform(auth(post("/api/purchases"), billing)
                .contentType(MediaType.APPLICATION_JSON).content(purchaseBody))
                .andExpect(status().isConflict());

        mvc.perform(auth(post("/api/purchases/" + purchase.get("id").asText() + "/cancel"), billing))
                .andExpect(status().isOk());

        JsonNode itemCancelled = body(mvc.perform(auth(get("/api/items"), billing))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode row : itemCancelled) {
            if (itemId.equals(row.get("id").asText())) {
                assertThat(row.get("stockMilli").asLong()).isEqualTo(5_000);
                assertThat(row.get("costPaise").asLong()).isZero();
                break;
            }
        }

        JsonNode item2 = body(mvc.perform(auth(post("/api/items"), admin).with(asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Towel","hsn":"6302","ratePaise":50000,"gstRateBp":1200,"stockMilli":0}"""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String item2Id = item2.get("id").asText();
        mvc.perform(auth(post("/api/purchases"), billing)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"supplierId":"%s","billNo":"PB-002","billDate":"%s",
                         "lines":[{"itemId":"%s","qtyMilli":1000,"costPaise":30000,"gstRateBp":1200}]}"""
                        .formatted(supplier.get("id").asText(), today, item2Id)))
                .andExpect(status().isOk());
        JsonNode secondPurchase = body(mvc.perform(auth(post("/api/purchases"), billing)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"supplierId":"%s","billNo":"PB-003","billDate":"%s",
                         "lines":[{"itemId":"%s","qtyMilli":1000,"costPaise":40000,"gstRateBp":1200}]}"""
                        .formatted(supplier.get("id").asText(), today, item2Id)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode afterSecond = body(mvc.perform(auth(get("/api/items"), billing))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long costAfterSecond = 0;
        for (JsonNode row : afterSecond) {
            if (item2Id.equals(row.get("id").asText())) {
                costAfterSecond = row.get("costPaise").asLong();
                break;
            }
        }
        assertThat(costAfterSecond).isEqualTo(40_000);
        mvc.perform(auth(post("/api/purchases/" + secondPurchase.get("id").asText() + "/cancel"), billing))
                .andExpect(status().isOk());
        JsonNode afterCancelSecond = body(mvc.perform(auth(get("/api/items"), billing))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (JsonNode row : afterCancelSecond) {
            if (item2Id.equals(row.get("id").asText())) {
                assertThat(row.get("costPaise").asLong()).isEqualTo(30_000);
                break;
            }
        }

        mvc.perform(auth(post("/api/admin/users"), admin).with(asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Till","username":"till","password":"pin1","role":"CASHIER","webAccess":true}"""))
                .andExpect(status().isOk());
        String cashier = login("till", "pin1", false);
        mvc.perform(auth(post("/api/purchases"), cashier)
                .contentType(MediaType.APPLICATION_JSON).content(purchaseBody))
                .andExpect(status().isForbidden());
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
