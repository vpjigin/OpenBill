package app.billing.print;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import app.billing.common.ApiException;
import app.billing.settings.SettingsService;
import app.billing.settings.ShopSettings;

@SpringBootTest(properties = {"app.admin-port=0", "app.billing-port=0", "app.public-port=0"})
class PrintServiceTest {

    @DynamicPropertySource
    static void dataDir(DynamicPropertyRegistry r) throws Exception {
        Path dir = Files.createTempDirectory("billing-print-test");
        Files.createDirectories(dir.resolve("logs"));
        r.add("app.data-dir", dir::toString);
    }

    static final List<byte[]> SENT = new ArrayList<>();

    @MockBean
    RawPrintTransport transport;

    @Autowired
    PrintService printService;

    @Autowired
    PrinterRepository printerRepo;

    @Autowired
    SettingsService settings;

    @BeforeEach
    void reset() throws Exception {
        SENT.clear();
        doAnswer(inv -> {
            SENT.add(inv.getArgument(0));
            return null;
        }).when(transport).send(any(), any());
    }

    @Test
    void printWithoutDefaultPrinterReturns409() {
        assertThatThrownBy(() -> printService.printInvoice(UUID.randomUUID(), null))
                .isInstanceOf(ApiException.class)
                .extracting(e -> ((ApiException) e).getStatus().value())
                .isEqualTo(409);
    }

    @Test
    void testPageUsesFakeTransport() {
        ensureShop();
        Printer p = new Printer();
        p.setName("Fake");
        p.setKind(PrinterKind.NETWORK);
        p.setHost("127.0.0.1");
        p.setPaperMm(80);
        p.setDefaultPrinter(true);
        p.setCut(false);
        printerRepo.save(p);

        var shop = ReceiptRenderer.ShopHeader.of(settings.get());
        printService.sendRaw(p, new ReceiptRenderer().renderTestPage(p, shop));
        assertThat(SENT).hasSize(1);
        assertThat(SENT.get(0).length).isGreaterThan(10);
    }

    private void ensureShop() {
        if (settings.find().isEmpty()) {
            ShopSettings s = new ShopSettings();
            s.setShopName("Test Shop");
            settings.create(s);
        }
    }
}
