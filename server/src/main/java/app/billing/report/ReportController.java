package app.billing.report;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import app.billing.common.ApiException;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportService reports;
    private final Gstr1ExportService gstr1;

    public ReportController(ReportService reports, Gstr1ExportService gstr1) {
        this.reports = reports;
        this.gstr1 = gstr1;
    }

    @GetMapping("/day-end")
    public ReportService.DayEndReport dayEnd(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        if (date == null) {
            throw ApiException.badRequest("date is required");
        }
        return reports.dayEnd(date);
    }

    @GetMapping("/sales")
    public List<ReportService.DailySalesRow> sales(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        try {
            return reports.salesRange(from, to);
        } catch (IllegalArgumentException e) {
            throw ApiException.badRequest(e.getMessage());
        }
    }

    @GetMapping("/hsn")
    public List<ReportService.HsnRow> hsn(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (from == null || to == null) {
            throw ApiException.badRequest("from and to are required");
        }
        if (to.isBefore(from)) {
            throw ApiException.badRequest("to must not be before from");
        }
        return reports.hsnSummary(from, to);
    }

    @GetMapping(value = "/gstr1", produces = "application/zip")
    public ResponseEntity<byte[]> gstr1(@RequestParam String month) {
        if (month == null || month.isBlank()) {
            throw ApiException.badRequest("month is required");
        }
        YearMonth ym;
        try {
            ym = YearMonth.parse(month.trim());
        } catch (Exception e) {
            throw ApiException.badRequest("month must be YYYY-MM");
        }
        Gstr1ExportService.Gstr1Zip zip = gstr1.export(ym);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zip.filename() + "\"")
                .body(zip.bytes());
    }
}
