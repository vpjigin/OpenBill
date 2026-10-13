package app.billing.supplier;

import java.util.UUID;

import app.billing.common.PageResult;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

@RestController
@RequestMapping("/api/suppliers")
public class SupplierController {

    private final SupplierService service;

    public SupplierController(SupplierService service) {
        this.service = service;
    }

    public record SupplierDto(UUID id, String name, String phone, String gstin, String stateCode, String address,
                              long version) {
        static SupplierDto of(Supplier s) {
            return new SupplierDto(s.getId(), s.getName(), s.getPhone(), s.getGstin(), s.getStateCode(),
                    s.getAddress(), s.getVersion());
        }
    }

    public record SupplierInput(@NotBlank String name, String phone, String gstin, String stateCode, String address,
                                Long version) {
        SupplierService.SupplierData toData() {
            return new SupplierService.SupplierData(name, phone, gstin, stateCode, address);
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResult<SupplierDto> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        PageResult<Supplier> pageResult = service.list(q, page, size);
        return new PageResult<>(
                pageResult.items().stream().map(SupplierDto::of).toList(),
                pageResult.total(),
                pageResult.page(),
                pageResult.size());
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public SupplierDto get(@PathVariable UUID id) {
        return SupplierDto.of(service.get(id));
    }

    @PostMapping
    @Transactional
    public SupplierDto create(@Valid @RequestBody SupplierInput in) {
        return SupplierDto.of(service.create(in.toData()));
    }

    @PutMapping("/{id}")
    @Transactional
    public SupplierDto update(@PathVariable UUID id, @Valid @RequestBody SupplierInput in) {
        return SupplierDto.of(service.update(id, in.toData(), in.version()));
    }
}
