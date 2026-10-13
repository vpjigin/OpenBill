package app.billing.print;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.billing.common.ApiException;
import app.billing.settings.SettingsService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Service
public class PrinterAdminService {

    private final PrinterRepository repo;
    private final PrintService printService;
    private final SettingsService settings;

    public PrinterAdminService(PrinterRepository repo, @Lazy PrintService printService, SettingsService settings) {
        this.repo = repo;
        this.printService = printService;
        this.settings = settings;
    }

    public record PrinterDto(UUID id, String name, PrinterKind kind, String systemName, String host, int port,
                             int paperMm, Integer charsPerLine, boolean openDrawer, boolean cut, boolean defaultPrinter,
                             long version) {
        static PrinterDto of(Printer p) {
            return new PrinterDto(p.getId(), p.getName(), p.getKind(), p.getSystemName(), p.getHost(), p.getPort(),
                    p.getPaperMm(), p.getCharsPerLine(), p.isOpenDrawer(), p.isCut(), p.isDefaultPrinter(),
                    p.getVersion());
        }
    }

    public record PrinterInput(@NotBlank String name, @NotNull PrinterKind kind, String systemName, String host,
                               @Min(1) @Max(65535) Integer port, @Min(58) @Max(80) int paperMm,
                               @Min(16) @Max(64) Integer charsPerLine, boolean openDrawer, boolean cut,
                               Long version) {
    }

    @Transactional(readOnly = true)
    public List<String> listOsPrinters() {
        return Arrays.asList(SystemPrintTransport.listSystemPrinterNames());
    }

    @Transactional(readOnly = true)
    public List<PrinterDto> listSaved() {
        return repo.findAllActive().stream().map(PrinterDto::of).toList();
    }

    @Transactional
    public PrinterDto create(PrinterInput in) {
        Printer p = new Printer();
        apply(p, in);
        if (repo.findAllActive().isEmpty()) {
            p.setDefaultPrinter(true);
        }
        return PrinterDto.of(repo.save(p));
    }

    @Transactional
    public PrinterDto update(UUID id, PrinterInput in) {
        Printer p = find(id);
        if (in.version() != null && in.version() != p.getVersion()) {
            throw ApiException.conflict("Someone else changed this printer. Reload and try again.");
        }
        apply(p, in);
        return PrinterDto.of(repo.saveAndFlush(p));
    }

    @Transactional
    public void delete(UUID id) {
        Printer p = find(id);
        boolean wasDefault = p.isDefaultPrinter();
        p.markDeleted();
        repo.saveAndFlush(p);
        if (wasDefault) {
            repo.findAllActive().stream().findFirst().ifPresent(other -> {
                other.setDefaultPrinter(true);
                repo.save(other);
            });
        }
    }

    @Transactional
    public PrinterDto setDefault(UUID id) {
        Printer p = find(id);
        for (Printer other : repo.findAllActive()) {
            other.setDefaultPrinter(other.getId().equals(p.getId()));
        }
        p.setDefaultPrinter(true);
        repo.saveAndFlush(p);
        return PrinterDto.of(p);
    }

    @Transactional(readOnly = true)
    public void testPrint(UUID id) {
        Printer p = find(id);
        var shop = ReceiptRenderer.ShopHeader.of(settings.get());
        printService.sendRaw(p, new ReceiptRenderer().renderTestPage(p, shop));
    }

    private Printer find(UUID id) {
        return repo.findById(id).filter(p -> !p.isDeleted())
                .orElseThrow(() -> ApiException.notFound("Printer not found"));
    }

    private static void apply(Printer p, PrinterInput in) {
        p.setName(in.name().trim());
        p.setKind(in.kind());
        p.setPaperMm(in.paperMm() == 58 ? 58 : 80);
        p.setCharsPerLine(in.charsPerLine());
        p.setOpenDrawer(in.openDrawer());
        p.setCut(in.cut());
        if (in.kind() == PrinterKind.SYSTEM) {
            if (in.systemName() == null || in.systemName().isBlank()) {
                throw ApiException.badRequest("Pick an OS printer name");
            }
            p.setSystemName(in.systemName().trim());
            p.setHost(null);
        } else {
            if (in.host() == null || in.host().isBlank()) {
                throw ApiException.badRequest("Enter printer IP or host");
            }
            p.setHost(in.host().trim());
            p.setSystemName(null);
            p.setPort(in.port() == null ? 9100 : in.port());
        }
    }
}
