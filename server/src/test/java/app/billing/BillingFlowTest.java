package app.billing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.io.ByteArrayInputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

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
class BillingFlowTest {

    @DynamicPropertySource
    static void dataDir(DynamicPropertyRegistry r) throws Exception {
        Path dir = Files.createTempDirectory("billing-test");
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
    void setupBillAndNumberInvoices() throws Exception {
        mvc.perform(post("/api/setup").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/setup").with(asAdmin()).contentType(MediaType.APPLICATION_JSON).content("""
                {"shopName":"Test Shop","gstin":"","stateCode":"32",
                 "adminName":"Owner","username":"owner","password":"secret1"}"""))
                .andExpect(status().isOk());

        String adminToken = login("owner", "secret1", true);

        mvc.perform(auth(get("/api/admin/access"), adminToken).with(asAdmin())).andExpect(status().isOk());
        mvc.perform(auth(get("/api/admin/access"), adminToken)).andExpect(status().isNotFound());
        mvc.perform(auth(get("/api/auth/me"), adminToken)).andExpect(status().isUnauthorized());

        JsonNode item = body(mvc.perform(auth(post("/api/items"), adminToken).with(asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Wooden puzzle","hsn":"9503","ratePaise":45000,"gstRateBp":1200,"stockMilli":10000}"""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String itemId = item.get("id").asText();
        assertThat(itemId).hasSize(36);
        assertThat(itemId.charAt(14)).isEqualTo('7');

        String billingToken = login("owner", "secret1", false);
        String invoiceBody = """
                {"lines":[{"itemId":"%s","qtyMilli":2000}],"interState":false,"paymentMode":"UPI"}"""
                .formatted(itemId);

        JsonNode inv1 = body(mvc.perform(auth(post("/api/invoices"), billingToken)
                .contentType(MediaType.APPLICATION_JSON).content(invoiceBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(inv1.get("invoiceNo").asText()).matches("INV/\\d{2}-\\d{2}/000001");
        assertThat(inv1.get("grossPaise").asLong()).isEqualTo(90_000);
        assertThat(inv1.get("taxablePaise").asLong()).isEqualTo(90_000);
        assertThat(inv1.get("cgstPaise").asLong()).isEqualTo(5_400);
        assertThat(inv1.get("sgstPaise").asLong()).isEqualTo(5_400);
        assertThat(inv1.get("totalPaise").asLong()).isEqualTo(100_800);

        JsonNode inv2 = body(mvc.perform(auth(post("/api/invoices"), billingToken)
                .contentType(MediaType.APPLICATION_JSON).content(invoiceBody))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(inv2.get("invoiceNo").asText()).endsWith("/000002");

        mvc.perform(auth(get("/api/items"), billingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].stockMilli").value(6_000));

        mvc.perform(auth(get("/api/invoices"), adminToken).with(asAdmin())).andExpect(status().isNotFound());

        JsonNode rice = body(mvc.perform(auth(post("/api/items"), adminToken).with(asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Rice","unit":"KG","ratePaise":6000,"gstRateBp":500,"stockMilli":5000000}"""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode soap = body(mvc.perform(auth(post("/api/items"), adminToken).with(asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Soap","ratePaise":11800,"gstRateBp":1800,"taxInclusive":true,"stockMilli":10000}"""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        String mixedBill = """
                {"lines":[{"itemId":"%s","qtyMilli":1250},{"itemId":"%s","qtyMilli":2000}],
                 "interState":false,"paymentMode":"CASH"}"""
                .formatted(rice.get("id").asText(), soap.get("id").asText());

        JsonNode mixed = body(mvc.perform(auth(post("/api/invoices"), billingToken)
                .contentType(MediaType.APPLICATION_JSON).content(mixedBill))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        assertThat(mixed.get("grossPaise").asLong()).isEqualTo(7_500 + 23_600);
        assertThat(mixed.get("totalPaise").asLong()).isEqualTo(31_500);

        mvc.perform(auth(get("/api/items?q=Rice"), billingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].stockMilli").value(4_998_750));

        JsonNode notebook = body(mvc.perform(auth(post("/api/items"), adminToken).with(asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Notebook","ratePaise":5000,"gstRateBp":1200,"stockMilli":5000}"""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        mvc.perform(auth(post("/api/invoices"), billingToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"lines":[{"itemId":"%s","qtyMilli":1500}],"interState":false,"paymentMode":"CASH"}"""
                        .formatted(notebook.get("id").asText())))
                .andExpect(status().isBadRequest());

        JsonNode discItem = body(mvc.perform(auth(post("/api/items"), adminToken).with(asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Shirt","ratePaise":100000,"gstRateBp":500,"stockMilli":10000}"""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode lineDiscInv = body(mvc.perform(auth(post("/api/invoices"), billingToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"lines":[{"itemId":"%s","qtyMilli":1000,"discountPaise":10000}],
                         "interState":false,"paymentMode":"CASH","billDiscountPaise":0}"""
                        .formatted(discItem.get("id").asText())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(lineDiscInv.get("grossPaise").asLong()).isEqualTo(100_000);
        assertThat(lineDiscInv.get("discountPaise").asLong()).isEqualTo(10_000);
        assertThat(lineDiscInv.get("taxablePaise").asLong()).isEqualTo(90_000);

        JsonNode cheap = body(mvc.perform(auth(post("/api/items"), adminToken).with(asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Pin","ratePaise":10000,"gstRateBp":0,"stockMilli":10000}"""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode pricey = body(mvc.perform(auth(post("/api/items"), adminToken).with(asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Bag","ratePaise":20000,"gstRateBp":0,"stockMilli":10000}"""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode billDiscInv = body(mvc.perform(auth(post("/api/invoices"), billingToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"lines":[{"itemId":"%s","qtyMilli":1000},{"itemId":"%s","qtyMilli":1000}],
                         "interState":false,"paymentMode":"CASH","billDiscountPaise":1000}"""
                        .formatted(cheap.get("id").asText(), pricey.get("id").asText())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(billDiscInv.get("billDiscountPaise").asLong()).isEqualTo(1_000);
        assertThat(billDiscInv.get("discountPaise").asLong()).isEqualTo(1_000);
        assertThat(billDiscInv.get("taxablePaise").asLong()).isEqualTo(29_000);

        mvc.perform(auth(post("/api/invoices"), billingToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"lines":[{"itemId":"%s","qtyMilli":1000,"discountPaise":50000}],
                         "interState":false,"paymentMode":"CASH"}"""
                        .formatted(cheap.get("id").asText())))
                .andExpect(status().isBadRequest());

        mvc.perform(auth(post("/api/customers"), billingToken).contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"Bad Co\",\"gstin\":\"NOT-A-GSTIN\"}"))
                .andExpect(status().isBadRequest());

        JsonNode tnCust = body(mvc.perform(auth(post("/api/customers"), billingToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"TN Buyer","gstin":"33AAAAA0000A1ZX"}"""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(tnCust.get("stateCode").asText()).isEqualTo("33");

        mvc.perform(auth(get("/api/customers?q=TN"), billingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].name").value("TN Buyer"));

        JsonNode interInv = body(mvc.perform(auth(post("/api/invoices"), billingToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"lines":[{"itemId":"%s","qtyMilli":1000}],"customerId":"%s",
                         "interState":false,"paymentMode":"CASH"}"""
                        .formatted(itemId, tnCust.get("id").asText())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(interInv.get("interState").asBoolean()).isTrue();
        assertThat(interInv.get("igstPaise").asLong()).isGreaterThan(0);
        assertThat(interInv.get("cgstPaise").asLong()).isZero();

        JsonNode klCust = body(mvc.perform(auth(post("/api/customers"), billingToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Local Buyer","gstin":"32AAAAA0000A1ZX"}"""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode intraInv = body(mvc.perform(auth(post("/api/invoices"), billingToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"lines":[{"itemId":"%s","qtyMilli":1000}],"customerId":"%s",
                         "interState":true,"paymentMode":"CASH"}"""
                        .formatted(itemId, klCust.get("id").asText())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(intraInv.get("interState").asBoolean()).isFalse();
        assertThat(intraInv.get("cgstPaise").asLong()).isGreaterThan(0);
        assertThat(intraInv.get("sgstPaise").asLong()).isGreaterThan(0);
        assertThat(intraInv.get("igstPaise").asLong()).isZero();

        mvc.perform(auth(get("/api/shop"), billingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stateCode").value("32"));
        mvc.perform(auth(get("/api/customers"), adminToken).with(asAdmin())).andExpect(status().isNotFound());

        String invId = inv1.get("id").asText();
        mvc.perform(auth(get("/api/invoices/" + invId), billingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.invoiceNo").value(inv1.get("invoiceNo").asText()))
                .andExpect(jsonPath("$.lines.length()").value(1))
                .andExpect(jsonPath("$.createdByName").value("Owner"));

        mvc.perform(auth(get("/api/invoices?q=INV/"), billingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].invoiceNo").exists());

        mvc.perform(auth(get("/api/invoices/" + invId), adminToken).with(asAdmin()))
                .andExpect(status().isNotFound());

        mvc.perform(auth(get("/api/invoices/00000000-0000-0000-0000-000000000000"), billingToken))
                .andExpect(status().isNotFound());

        JsonNode invDetail = body(mvc.perform(auth(get("/api/invoices/" + invId), billingToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String invoiceLineId = invDetail.get("lines").get(0).get("id").asText();
        assertThat(invDetail.get("lines").get(0).get("returnableQtyMilli").asLong()).isEqualTo(2_000);

        JsonNode cn1 = body(mvc.perform(auth(post("/api/credit-notes"), billingToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"invoiceId":"%s","reason":"Damaged","refundMode":"CASH",
                         "lines":[{"invoiceLineId":"%s","qtyMilli":1000}]}"""
                        .formatted(invId, invoiceLineId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(cn1.get("cnNo").asText()).matches("CN/\\d{2}-\\d{2}/000001");
        assertThat(cn1.get("taxablePaise").asLong()).isEqualTo(45_000);
        assertThat(cn1.get("cgstPaise").asLong()).isEqualTo(2_700);
        assertThat(cn1.get("sgstPaise").asLong()).isEqualTo(2_700);
        assertThat(cn1.get("totalPaise").asLong()).isEqualTo(50_400);

        JsonNode allItems = body(mvc.perform(auth(get("/api/items"), billingToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long puzzleStock = 0;
        for (JsonNode it : allItems.get("items")) {
            if (itemId.equals(it.get("id").asText())) {
                puzzleStock = it.get("stockMilli").asLong();
                break;
            }
        }
        assertThat(puzzleStock).isEqualTo(5_000);

        JsonNode afterPartial = body(mvc.perform(auth(get("/api/invoices/" + invId), billingToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(afterPartial.get("lines").get(0).get("returnableQtyMilli").asLong()).isEqualTo(1_000);
        assertThat(afterPartial.get("creditNotes").size()).isEqualTo(1);

        JsonNode cn2 = body(mvc.perform(auth(post("/api/credit-notes"), billingToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"invoiceId":"%s","reason":"Rest","refundMode":"UPI",
                         "lines":[{"invoiceLineId":"%s","qtyMilli":1000}]}"""
                        .formatted(invId, invoiceLineId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(cn2.get("cnNo").asText()).endsWith("/000002");
        assertThat(cn2.get("taxablePaise").asLong()).isEqualTo(45_000);
        assertThat(cn2.get("totalPaise").asLong()).isEqualTo(50_400);

        JsonNode finished = body(mvc.perform(auth(get("/api/invoices/" + invId), billingToken))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(finished.get("lines").get(0).get("returnableQtyMilli").asLong()).isZero();

        mvc.perform(auth(post("/api/credit-notes"), billingToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"invoiceId":"%s","reason":"Too much","refundMode":"CASH",
                         "lines":[{"invoiceLineId":"%s","qtyMilli":1}]}"""
                        .formatted(invId, invoiceLineId)))
                .andExpect(status().isBadRequest());

        mvc.perform(auth(post("/api/admin/users"), adminToken).with(asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Till","username":"till","password":"pin1","role":"CASHIER","webAccess":true}"""))
                .andExpect(status().isOk());
        String cashierToken = login("till", "pin1", false);
        mvc.perform(auth(post("/api/credit-notes"), cashierToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"invoiceId":"%s","reason":"Nope","refundMode":"CASH",
                         "lines":[{"invoiceLineId":"%s","qtyMilli":1000}]}"""
                        .formatted(invId, invoiceLineId)))
                .andExpect(status().isForbidden());

        mvc.perform(auth(get("/api/credit-notes"), adminToken).with(asAdmin())).andExpect(status().isNotFound());

        String heldPayload = """
                {"v":1,"lines":[{"itemId":"%s","qtyMilli":1000,
                  "discount":{"value":"","mode":"rupee"}}],
                 "billDiscount":{"value":"","mode":"rupee"},
                 "interState":false,"payment":"CASH",
                 "selectedCustomerId":null,"inlineCustomer":null,
                 "showNewCustomer":false,"saveCustomer":false,
                 "previewTotalPaise":50400}"""
                .formatted(itemId);
        JsonNode held = body(mvc.perform(auth(post("/api/held-bills"), billingToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"label\":\"Counter A\",\"payload\":" + json.writeValueAsString(heldPayload) + "}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(held.get("itemCount").asInt()).isEqualTo(1);
        assertThat(held.get("previewTotalPaise").asLong()).isEqualTo(50_400);
        String heldId = held.get("id").asText();

        mvc.perform(auth(get("/api/held-bills"), billingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(heldId))
                .andExpect(jsonPath("$[0].label").value("Counter A"));

        mvc.perform(auth(get("/api/held-bills/" + heldId), billingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payload").isNotEmpty());

        mvc.perform(auth(delete("/api/held-bills/" + heldId), billingToken))
                .andExpect(status().isOk());

        mvc.perform(auth(get("/api/held-bills"), billingToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));

        String big = "x".repeat(200 * 1024 + 1);
        mvc.perform(auth(post("/api/held-bills"), billingToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"payload\":" + json.writeValueAsString(big) + "}"))
                .andExpect(status().isBadRequest());

        mvc.perform(auth(get("/api/held-bills"), adminToken).with(asAdmin())).andExpect(status().isNotFound());

        JsonNode tillUser = body(mvc.perform(auth(post("/api/admin/users"), adminToken).with(asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Phone Till","username":"phone","password":"oldpin","role":"CASHIER","webAccess":true}"""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String tillId = tillUser.get("id").asText();
        String tillSession = login("phone", "oldpin", false);
        mvc.perform(auth(get("/api/auth/me"), tillSession)).andExpect(status().isOk());

        mvc.perform(auth(post("/api/admin/users/" + tillId + "/reset-password"), adminToken).with(asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"newPassword\":\"newpin9\"}"))
                .andExpect(status().isOk());

        mvc.perform(auth(get("/api/auth/me"), tillSession)).andExpect(status().isUnauthorized());
        login("phone", "newpin9", false);

        String sessionA = login("owner", "secret1", false);
        String sessionB = login("owner", "secret1", false);
        mvc.perform(auth(get("/api/auth/me"), sessionB)).andExpect(status().isOk());

        mvc.perform(auth(post("/api/auth/change-password"), sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"wrong\",\"newPassword\":\"newpass1\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(auth(post("/api/auth/change-password"), sessionA)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"currentPassword\":\"secret1\",\"newPassword\":\"newpass1\"}"))
                .andExpect(status().isOk());

        mvc.perform(auth(get("/api/auth/me"), sessionB)).andExpect(status().isUnauthorized());
        mvc.perform(auth(get("/api/auth/me"), sessionA)).andExpect(status().isOk());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"owner\",\"password\":\"secret1\"}"))
                .andExpect(status().isUnauthorized());

        login("owner", "newpass1", false);

        String adminAfterPw = login("owner", "newpass1", true);
        int lanStartStatus = mvc.perform(auth(post("/api/admin/access/lan/start"), adminAfterPw).with(asAdmin()))
                .andReturn().getResponse().getStatus();
        assertThat(lanStartStatus).isIn(200, 409);

        JsonNode activity = body(mvc.perform(auth(get("/api/admin/activity?limit=50"), adminAfterPw).with(asAdmin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        boolean hasLogin = false;
        boolean hasLan = false;
        for (JsonNode row : activity) {
            String act = row.get("action").asText();
            if ("LOGIN".equals(act)) {
                hasLogin = true;
            }
            if ("LAN_STARTED".equals(act)) {
                hasLan = true;
            }
        }
        assertThat(hasLogin).isTrue();
        if (lanStartStatus == 200) {
            assertThat(hasLan).isTrue();
        }

        mvc.perform(auth(get("/api/admin/activity"), billingToken)).andExpect(status().isNotFound());

        String adminStock = login("owner", "newpass1", true);
        String billingAfterPw = login("owner", "newpass1", false);

        body(mvc.perform(auth(post("/api/items"), adminStock).with(asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Low Stock Test","ratePaise":10000,"gstRateBp":0,"stockMilli":3000,
                         "reorderLevelMilli":5000}"""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        mvc.perform(auth(get("/api/items/low-stock"), billingAfterPw))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].name").value("Low Stock Test"));

        JsonNode attention = body(mvc.perform(auth(get("/api/admin/attention"), adminStock).with(asAdmin()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        boolean lowStockWarn = false;
        boolean invoiceOk = false;
        for (JsonNode row : attention) {
            if ("LOW_STOCK".equals(row.get("code").asText()) && "warn".equals(row.get("level").asText())) {
                lowStockWarn = true;
            }
            if ("INVOICE_SERIES".equals(row.get("code").asText()) && "ok".equals(row.get("level").asText())) {
                invoiceOk = true;
            }
        }
        assertThat(lowStockWarn).isTrue();
        assertThat(invoiceOk).isTrue();

        mvc.perform(auth(get("/api/admin/attention"), billingToken)).andExpect(status().isNotFound());

        String todayStr = LocalDate.now(ZoneId.systemDefault()).toString();
        JsonNode dayBefore = body(mvc.perform(auth(get("/api/reports/day-end?date=" + todayStr), billingAfterPw))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long billsBefore = dayBefore.get("totals").get("billsCount").asLong();
        long totalBefore = dayBefore.get("totals").get("totalPaise").asLong();
        long netCashBefore = netPaiseForMode(dayBefore, "CASH");
        long netUpiBefore = netPaiseForMode(dayBefore, "UPI");

        JsonNode reportItem = body(mvc.perform(auth(post("/api/items"), adminStock).with(asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"Report Widget","hsn":"1234","ratePaise":10000,"gstRateBp":0,"stockMilli":100000}"""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String reportItemId = reportItem.get("id").asText();

        JsonNode invCash = body(mvc.perform(auth(post("/api/invoices"), billingAfterPw)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"lines":[{"itemId":"%s","qtyMilli":1000}],"interState":false,"paymentMode":"CASH"}"""
                        .formatted(reportItemId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode invUpi = body(mvc.perform(auth(post("/api/invoices"), billingAfterPw)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"lines":[{"itemId":"%s","qtyMilli":1000}],"interState":false,"paymentMode":"UPI"}"""
                        .formatted(reportItemId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode cashDetail = body(mvc.perform(auth(get("/api/invoices/" + invCash.get("id").asText()), billingAfterPw))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String reportLineId = cashDetail.get("lines").get(0).get("id").asText();
        mvc.perform(auth(post("/api/credit-notes"), billingAfterPw)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"invoiceId":"%s","reason":"Report test","refundMode":"CASH",
                         "lines":[{"invoiceLineId":"%s","qtyMilli":500}]}"""
                        .formatted(invCash.get("id").asText(), reportLineId)))
                .andExpect(status().isOk());

        JsonNode dayAfter = body(mvc.perform(auth(get("/api/reports/day-end?date=" + todayStr), billingAfterPw))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(dayAfter.get("totals").get("billsCount").asLong()).isEqualTo(billsBefore + 2);
        assertThat(dayAfter.get("totals").get("totalPaise").asLong()).isEqualTo(totalBefore + 20_000);
        assertThat(netPaiseForMode(dayAfter, "CASH") - netCashBefore).isEqualTo(5_000);
        assertThat(netPaiseForMode(dayAfter, "UPI") - netUpiBefore).isEqualTo(10_000);
        assertThat(dayAfter.get("firstInvoiceNo").asText()).isNotBlank();
        assertThat(dayAfter.get("lastInvoiceNo").asText()).isEqualTo(invUpi.get("invoiceNo").asText());

        JsonNode hsnRows = body(mvc.perform(auth(get("/api/reports/hsn?from=" + todayStr + "&to=" + todayStr), billingAfterPw))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode hsn1234 = null;
        for (JsonNode row : hsnRows) {
            if ("1234".equals(row.get("hsn").asText())) {
                hsn1234 = row;
                break;
            }
        }
        assertThat(hsn1234).isNotNull();
        assertThat(hsn1234.get("qtyMilli").asLong()).isEqualTo(1_500);
        assertThat(hsn1234.get("taxablePaise").asLong()).isEqualTo(15_000);

        String monthStr = YearMonth.now(ZoneId.systemDefault()).toString();
        String monthFrom = monthStr + "-01";
        String monthTo = YearMonth.parse(monthStr).atEndOfMonth().toString();

        JsonNode gstrB2bCust = body(mvc.perform(auth(post("/api/customers"), billingAfterPw)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"GSTR B2B","gstin":"32BBBBB0000B1Z5"}"""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode gstrSmallItem = body(mvc.perform(auth(post("/api/items"), adminStock).with(asAdmin())
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"GSTR Small B2C","hsn":"6101","ratePaise":5000,"gstRateBp":500,"stockMilli":50000}"""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode gstrB2bInv = body(mvc.perform(auth(post("/api/invoices"), billingAfterPw)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"lines":[{"itemId":"%s","qtyMilli":1000}],"customerId":"%s",
                         "interState":false,"paymentMode":"CASH"}"""
                        .formatted(gstrSmallItem.get("id").asText(), gstrB2bCust.get("id").asText())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode gstrB2cInv = body(mvc.perform(auth(post("/api/invoices"), billingAfterPw)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"lines":[{"itemId":"%s","qtyMilli":1000}],"interState":false,"paymentMode":"CASH"}"""
                        .formatted(gstrSmallItem.get("id").asText())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode gstrB2clCust = body(mvc.perform(auth(post("/api/customers"), billingAfterPw)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"name":"GSTR B2CL","stateCode":"33"}"""))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode gstrB2clInv = body(mvc.perform(auth(post("/api/invoices"), billingAfterPw)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"lines":[{"itemId":"%s","qtyMilli":25000000}],"customerId":"%s",
                         "interState":true,"paymentMode":"UPI"}"""
                        .formatted(gstrSmallItem.get("id").asText(), gstrB2clCust.get("id").asText())))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(gstrB2clInv.get("totalPaise").asLong()).isGreaterThan(10_000_000L);

        JsonNode gstrB2bDetail = body(mvc.perform(auth(get("/api/invoices/" + gstrB2bInv.get("id").asText()), billingAfterPw))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        String gstrLineId = gstrB2bDetail.get("lines").get(0).get("id").asText();
        JsonNode gstrCn = body(mvc.perform(auth(post("/api/credit-notes"), billingAfterPw)
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"invoiceId":"%s","reason":"GSTR test","refundMode":"CASH",
                         "lines":[{"invoiceLineId":"%s","qtyMilli":500}]}"""
                        .formatted(gstrB2bInv.get("id").asText(), gstrLineId)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode hsnMonth = body(mvc.perform(auth(get("/api/reports/hsn?from=" + monthFrom + "&to=" + monthTo), billingAfterPw))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        long hsnTaxablePaise = 0;
        for (JsonNode row : hsnMonth) {
            hsnTaxablePaise += row.get("taxablePaise").asLong();
        }

        byte[] zipBytes = mvc.perform(auth(get("/api/reports/gstr1?month=" + monthStr), billingAfterPw))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();

        Map<String, String> entries = unzipUtf8(zipBytes);
        assertThat(entries.keySet()).containsExactlyInAnyOrder(
                "b2b.csv", "b2cl.csv", "b2cs.csv", "cdnr.csv", "hsn.csv", "docs.csv");

        assertThat(entries.get("b2b.csv")).contains("32BBBBB0000B1Z5");
        assertThat(entries.get("b2b.csv")).contains(gstrB2bInv.get("invoiceNo").asText());
        assertThat(entries.get("b2cl.csv")).contains(gstrB2clInv.get("invoiceNo").asText());
        assertThat(entries.get("b2cs.csv")).contains("OE");
        assertThat(entries.get("b2cs.csv")).contains("32-Kerala");
        assertThat(entries.get("cdnr.csv")).contains(gstrCn.get("cnNo").asText());
        assertThat(entries.get("cdnr.csv")).contains("32BBBBB0000B1Z5");
        assertThat(entries.get("docs.csv")).contains("Invoices for outward supply");
        assertThat(entries.get("docs.csv")).contains("Credit Note");

        long csvHsnTaxable = sumHsnCsvTaxablePaise(entries.get("hsn.csv"));
        assertThat(csvHsnTaxable).isEqualTo(hsnTaxablePaise);

        mvc.perform(auth(get("/api/reports/day-end?date=" + todayStr), adminStock).with(asAdmin()))
                .andExpect(status().isNotFound());
        String cashierReports = login("phone", "newpin9", false);
        mvc.perform(auth(get("/api/reports/day-end?date=" + todayStr), cashierReports))
                .andExpect(status().isForbidden());
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

    private JsonNode body(String s) throws Exception {
        return json.readTree(s);
    }

    private static long netPaiseForMode(JsonNode dayEnd, String mode) {
        for (JsonNode row : dayEnd.get("netByMode")) {
            if (mode.equals(row.get("mode").asText())) {
                return row.get("netPaise").asLong();
            }
        }
        return 0;
    }

    private static Map<String, String> unzipUtf8(byte[] zipBytes) throws Exception {
        Map<String, String> out = new HashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                out.put(entry.getName(), new String(zis.readAllBytes(), StandardCharsets.UTF_8));
                zis.closeEntry();
            }
        }
        return out;
    }

    private static long sumHsnCsvTaxablePaise(String csv) {
        String[] lines = csv.split("\n");
        long sum = 0;
        for (int i = 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isEmpty()) {
                continue;
            }
            String[] cols = line.split(",", -1);
            if (cols.length < 7) {
                continue;
            }
            sum += rupeesToPaise(cols[6]);
        }
        return sum;
    }

    private static long rupeesToPaise(String rupees) {
        return new BigDecimal(rupees.trim()).movePointRight(2).longValueExact();
    }
}
