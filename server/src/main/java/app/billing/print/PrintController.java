package app.billing.print;

import java.time.LocalDate;
import java.util.UUID;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/print")
public class PrintController {

    private final PrintService printService;

    public PrintController(PrintService printService) {
        this.printService = printService;
    }

    @GetMapping("/status")
    public PrintService.PrintStatus status() {
        return printService.status();
    }

    @PostMapping("/invoice/{id}")
    public void printInvoice(@PathVariable UUID id, @RequestParam(required = false) UUID printerId) {
        printService.printInvoice(id, printerId);
    }

    @PostMapping("/credit-note/{id}")
    public void printCreditNote(@PathVariable UUID id, @RequestParam(required = false) UUID printerId) {
        printService.printCreditNote(id, printerId);
    }

    @PostMapping("/day-end")
    public void printDayEnd(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) UUID printerId) {
        printService.printDayEnd(date, printerId);
    }

    @PostMapping("/drawer")
    public void openDrawer(@RequestParam(required = false) UUID printerId) {
        printService.openDrawer(printerId);
    }
}
