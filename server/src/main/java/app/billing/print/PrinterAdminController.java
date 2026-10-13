package app.billing.print;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/admin/printers")
public class PrinterAdminController {

    private final PrinterAdminService service;

    public PrinterAdminController(PrinterAdminService service) {
        this.service = service;
    }

    @GetMapping("/os")
    public List<String> osPrinters() {
        return service.listOsPrinters();
    }

    @GetMapping
    public List<PrinterAdminService.PrinterDto> list() {
        return service.listSaved();
    }

    @PostMapping
    public PrinterAdminService.PrinterDto create(@Valid @RequestBody PrinterAdminService.PrinterInput body) {
        return service.create(body);
    }

    @PutMapping("/{id}")
    public PrinterAdminService.PrinterDto update(@PathVariable UUID id,
                                                 @Valid @RequestBody PrinterAdminService.PrinterInput body) {
        return service.update(id, body);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }

    @PostMapping("/{id}/default")
    public PrinterAdminService.PrinterDto setDefault(@PathVariable UUID id) {
        return service.setDefault(id);
    }

    @PostMapping("/{id}/test")
    public void testPrint(@PathVariable UUID id) {
        service.testPrint(id);
    }
}
