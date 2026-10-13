package app.billing.customer;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.billing.common.ApiException;
import app.billing.common.GstStates;

@Service
public class CustomerService {

    private final CustomerRepository repo;

    public CustomerService(CustomerRepository repo) {
        this.repo = repo;
    }

    public record CustomerData(String name, String phone, String gstin, String stateCode, String address, String email,
                               String pincode) {
    }

    @Transactional(readOnly = true)
    public List<Customer> search(String q) {
        if (q == null || q.isBlank()) {
            return List.of();
        }
        return repo.search(q.trim(), PageRequest.of(0, 20));
    }

    @Transactional(readOnly = true)
    public Customer get(UUID id) {
        return repo.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("Customer not found"));
    }

    @Transactional
    public Customer create(CustomerData data) {
        Customer c = new Customer();
        apply(c, data);
        return repo.save(c);
    }

    @Transactional
    public Customer update(UUID id, CustomerData data, Long expectedVersion) {
        Customer c = get(id);
        if (expectedVersion != null && expectedVersion != c.getVersion()) {
            throw ApiException.conflict("Someone else changed this customer. Reload and try again.");
        }
        apply(c, data);
        return repo.saveAndFlush(c);
    }

    static void apply(Customer c, CustomerData data) {
        if (data.name() == null || data.name().isBlank()) {
            throw ApiException.badRequest("Customer name is required");
        }
        c.setName(data.name().trim());
        c.setPhone(blankToNull(data.phone()));
        c.setEmail(blankToNull(data.email()));
        c.setAddress(blankToNull(data.address()));
        String pin = blankToNull(data.pincode());
        if (pin != null && !pin.matches("\\d{6}")) {
            throw ApiException.badRequest("Pincode must be 6 digits");
        }
        c.setPincode(pin);

        String gstin = blankToNull(data.gstin());
        if (gstin != null) {
            gstin = gstin.toUpperCase();
            if (!gstin.matches(GstStates.GSTIN_PATTERN)) {
                throw ApiException.badRequest("Invalid GSTIN format");
            }
            c.setGstin(gstin);
            c.setStateCode(GstStates.stateCodeFromGstin(gstin).orElseThrow(
                    () -> ApiException.badRequest("GSTIN state code is not valid")));
        } else {
            c.setGstin(null);
            String state = blankToNull(data.stateCode());
            if (state != null && !GstStates.isValidCode(state)) {
                throw ApiException.badRequest("Invalid state code");
            }
            c.setStateCode(state);
        }
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
