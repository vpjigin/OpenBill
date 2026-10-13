package app.billing.customer;

import java.util.List;
import java.util.UUID;

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
@RequestMapping("/api/customers")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    public record CustomerDto(UUID id, String name, String phone, String gstin, String stateCode, String address,
                            String email, String pincode, long version) {
        static CustomerDto of(Customer c) {
            return new CustomerDto(c.getId(), c.getName(), c.getPhone(), c.getGstin(), c.getStateCode(),
                    c.getAddress(), c.getEmail(), c.getPincode(), c.getVersion());
        }
    }

    public record CustomerInput(@NotBlank String name, String phone, String gstin, String stateCode, String address,
                                String email, String pincode, Long version) {
        CustomerService.CustomerData toData() {
            return new CustomerService.CustomerData(name, phone, gstin, stateCode, address, email, pincode);
        }
    }

    @GetMapping
    @Transactional(readOnly = true)
    public List<CustomerDto> search(@RequestParam(required = false) String q) {
        return service.search(q).stream().map(CustomerDto::of).toList();
    }

    @GetMapping("/{id}")
    @Transactional(readOnly = true)
    public CustomerDto get(@PathVariable UUID id) {
        return CustomerDto.of(service.get(id));
    }

    @PostMapping
    @Transactional
    public CustomerDto create(@Valid @RequestBody CustomerInput in) {
        return CustomerDto.of(service.create(in.toData()));
    }

    @PutMapping("/{id}")
    @Transactional
    public CustomerDto update(@PathVariable UUID id, @Valid @RequestBody CustomerInput in) {
        return CustomerDto.of(service.update(id, in.toData(), in.version()));
    }
}
