package app.billing.held;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import app.billing.auth.CurrentUser;
import app.billing.common.ApiException;
import app.billing.user.UserRepository;

@Service
public class HeldBillService {

    static final int MAX_PAYLOAD_BYTES = 200 * 1024;

    private final HeldBillRepository repo;
    private final UserRepository users;
    private final ObjectMapper json;

    public HeldBillService(HeldBillRepository repo, UserRepository users, ObjectMapper json) {
        this.repo = repo;
        this.users = users;
        this.json = json;
    }

    public record CreateRequest(String label, String payload) {
    }

    public record HeldBillDto(UUID id, String label, int itemCount, Long previewTotalPaise, String createdByName,
                              Instant createdAt) {
    }

    public record HeldBillDetailDto(UUID id, String label, String payload, String createdByName, Instant createdAt) {
    }

    @Transactional(readOnly = true)
    public List<HeldBillDto> list() {
        return repo.findActiveNewestFirst().stream().map(this::toDto).toList();
    }

    @Transactional(readOnly = true)
    public HeldBillDetailDto get(UUID id) {
        HeldBill h = repo.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("Held bill not found"));
        String name = users.findById(h.getCreatedBy()).map(u -> u.getName()).orElse("—");
        return new HeldBillDetailDto(h.getId(), h.getLabel(), h.getPayload(), name, h.getCreatedAt());
    }

    @Transactional
    public HeldBillDto create(CreateRequest req, CurrentUser by) {
        if (req.payload() == null || req.payload().isBlank()) {
            throw ApiException.badRequest("Payload is required");
        }
        byte[] bytes = req.payload().getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_PAYLOAD_BYTES) {
            throw ApiException.badRequest("Held bill is too large (max 200 KB)");
        }
        HeldBill h = new HeldBill();
        h.setLabel(blankToNull(req.label()));
        h.setPayload(req.payload());
        h.setCreatedBy(by.userId());
        h.setChannel(by.channel());
        return toDto(repo.save(h));
    }

    @Transactional
    public void delete(UUID id) {
        HeldBill h = repo.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> ApiException.notFound("Held bill not found"));
        h.markDeleted();
    }

    /** Drop held bills older than 7 days (runs once per day). */
    @Scheduled(cron = "0 5 0 * * *")
    @Transactional
    public void purgeExpired() {
        Instant cutoff = Instant.now().minus(7, ChronoUnit.DAYS);
        repo.softDeleteOlderThan(cutoff, Instant.now());
    }

    private HeldBillDto toDto(HeldBill h) {
        PayloadMeta meta = parseMeta(h.getPayload());
        String name = users.findById(h.getCreatedBy()).map(u -> u.getName()).orElse("—");
        return new HeldBillDto(h.getId(), h.getLabel(), meta.itemCount(), meta.previewTotalPaise(), name,
                h.getCreatedAt());
    }

    private PayloadMeta parseMeta(String payload) {
        try {
            JsonNode root = json.readTree(payload);
            int count = root.path("lines").isArray() ? root.path("lines").size() : 0;
            JsonNode preview = root.get("previewTotalPaise");
            Long total = preview != null && preview.isNumber() ? preview.longValue() : null;
            return new PayloadMeta(count, total);
        } catch (Exception e) {
            return new PayloadMeta(0, null);
        }
    }

    private record PayloadMeta(int itemCount, Long previewTotalPaise) {
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
