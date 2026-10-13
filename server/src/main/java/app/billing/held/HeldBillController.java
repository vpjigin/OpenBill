package app.billing.held;

import java.util.List;
import java.util.UUID;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import app.billing.auth.CurrentUser;

@RestController
@RequestMapping("/api/held-bills")
public class HeldBillController {

    private final HeldBillService service;

    public HeldBillController(HeldBillService service) {
        this.service = service;
    }

    @GetMapping
    public List<HeldBillService.HeldBillDto> list() {
        return service.list();
    }

    @PostMapping
    public HeldBillService.HeldBillDto create(@RequestBody HeldBillService.CreateRequest body) {
        return service.create(body, CurrentUser.get());
    }

    @GetMapping("/{id}")
    public HeldBillService.HeldBillDetailDto get(@PathVariable UUID id) {
        return service.get(id);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable UUID id) {
        service.delete(id);
    }
}
