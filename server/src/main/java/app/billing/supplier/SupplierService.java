package app.billing.supplier;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import app.billing.common.ApiException;
import app.billing.common.PageResult;
import app.billing.common.GstStates;

@Service
public class SupplierService {

    private final SupplierRepository repo;

    public SupplierService(SupplierRepository repo) {
        this.repo = repo;
    }

    public record SupplierData(String name, String phone, String gstin, String stateCode, String address) {
    }

    @Transactional(readOnly = true)
    public PageResult<Supplier> list(String q, int page, int size) {
        String query = q == null || q.isBlank() ? null : q.trim();
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), 200);
        long total = repo.countSearch(query);
        List<Supplier> items = repo.search(query, PageRequest.of(p, s));
        return new PageResult<>(items, total, p, s);
    }

    @Transactional(readOnly = true)
    public Supplier get(UUID id) {
        return repo.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("Supplier not found"));
    }

    @Transactional
    public Supplier create(SupplierData data) {
        Supplier s = new Supplier();
        apply(s, data);
        return repo.save(s);
    }

    @Transactional
    public Supplier update(UUID id, SupplierData data, Long expectedVersion) {
        Supplier s = get(id);
        if (expectedVersion != null && expectedVersion != s.getVersion()) {
            throw ApiException.conflict("Someone else changed this supplier. Reload and try again.");
        }
        apply(s, data);
        return repo.saveAndFlush(s);
    }

    static void apply(Supplier s, SupplierData data) {
        if (data.name() == null || data.name().isBlank()) {
            throw ApiException.badRequest("Supplier name is required");
        }
        s.setName(data.name().trim());
        s.setPhone(blankToNull(data.phone()));
        s.setAddress(blankToNull(data.address()));

        String gstin = blankToNull(data.gstin());
        if (gstin != null) {
            gstin = gstin.toUpperCase();
            if (!gstin.matches(GstStates.GSTIN_PATTERN)) {
                throw ApiException.badRequest("Invalid GSTIN format");
            }
            s.setGstin(gstin);
            s.setStateCode(GstStates.stateCodeFromGstin(gstin).orElseThrow(
                    () -> ApiException.badRequest("GSTIN state code is not valid")));
        } else {
            s.setGstin(null);
            String state = blankToNull(data.stateCode());
            if (state != null && !GstStates.isValidCode(state)) {
                throw ApiException.badRequest("Invalid state code");
            }
            s.setStateCode(state);
        }
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
