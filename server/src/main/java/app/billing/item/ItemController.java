package app.billing.item;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;

import app.billing.common.PageResult;

import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import app.billing.activity.ActivityActions;
import app.billing.activity.ActivityService;
import app.billing.common.ApiException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

/** Read: any signed-in user. Write: ADMIN / MANAGER (see SecurityConfig). */
@RestController
@RequestMapping("/api/items")
public class ItemController {

    private final ItemRepository repo;
    private final ActivityService activity;

    public ItemController(ItemRepository repo, ActivityService activity) {
        this.repo = repo;
        this.activity = activity;
    }

    public record ItemDto(UUID id, String name, String code, String barcode, String hsn, String unit,
                          long ratePaise, long costPaise, int gstRateBp, long stockMilli, long reorderLevelMilli,
                          boolean taxInclusive, long version) {
        static ItemDto of(Item i) {
            return new ItemDto(i.getId(), i.getName(), i.getCode(), i.getBarcode(), i.getHsn(), i.getUnit(),
                    i.getRatePaise(), i.getCostPaise(), i.getGstRateBp(), i.getStockMilli(), i.getReorderLevelMilli(),
                    i.isTaxInclusive(), i.getVersion());
        }
    }

    public record ItemInput(@NotBlank String name, String code, String barcode, String hsn, String unit,
                            @Min(0) long ratePaise, @Min(0) @Max(4000) int gstRateBp, long stockMilli,
                            @Min(0) Long reorderLevelMilli, Boolean taxInclusive, Long version) {
    }

    @GetMapping
    @Transactional(readOnly = true)
    public PageResult<ItemDto> list(
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        String query = q == null || q.isBlank() ? null : q.trim();
        int p = Math.max(page, 0);
        int s = Math.min(Math.max(size, 1), 200);
        long total = repo.countSearch(query);
        List<Item> items = repo.search(query, PageRequest.of(p, s));
        return new PageResult<>(items.stream().map(ItemDto::of).toList(), total, p, s);
    }

    @GetMapping("/low-stock")
    @Transactional(readOnly = true)
    public List<ItemDto> lowStock() {
        return repo.findLowStock().stream().map(ItemDto::of).toList();
    }

    @PostMapping
    @Transactional
    public ItemDto create(@Valid @RequestBody ItemInput in) {
        Item i = new Item();
        apply(i, in);
        Item saved = repo.save(i);
        activity.log(ActivityActions.ITEM_CREATED, saved.getName(), saved.getId().toString());
        return ItemDto.of(saved);
    }

    @PutMapping("/{id}")
    @Transactional
    public ItemDto update(@PathVariable UUID id, @Valid @RequestBody ItemInput in) {
        Item i = find(id);
        if (in.version() != null && in.version() != i.getVersion()) {
            throw ApiException.conflict("Someone else changed this item. Reload and try again.");
        }
        apply(i, in);
        Item saved = repo.saveAndFlush(i);
        activity.log(ActivityActions.ITEM_UPDATED, saved.getName(), saved.getId().toString());
        return ItemDto.of(saved);
    }

    @PostMapping("/{id}/barcode")
    @Transactional
    public ItemDto generateBarcode(@PathVariable UUID id) {
        Item i = find(id);
        if (i.getBarcode() != null && !i.getBarcode().isBlank()) {
            return ItemDto.of(i);
        }
        String barcode = nextUniqueInternalBarcode();
        i.setBarcode(barcode);
        Item saved = repo.saveAndFlush(i);
        activity.log(ActivityActions.ITEM_UPDATED, saved.getName(), saved.getId().toString());
        return ItemDto.of(saved);
    }

    private String nextUniqueInternalBarcode() {
        long seq = repo.maxInternalBarcodeSequence();
        for (int attempt = 0; attempt < 20; attempt++) {
            seq++;
            String candidate = Ean13.formatInternal(seq);
            if (!repo.existsByBarcodeAndDeletedAtIsNull(candidate)) {
                return candidate;
            }
        }
        throw ApiException.conflict("Could not allocate a unique barcode. Try again.");
    }

    @DeleteMapping("/{id}")
    @Transactional
    public void delete(@PathVariable UUID id) {
        Item i = find(id);
        activity.log(ActivityActions.ITEM_DELETED, i.getName(), i.getId().toString());
        i.markDeleted();
    }

    private Item find(UUID id) {
        return repo.findById(id).filter(i -> !i.isDeleted())
                .orElseThrow(() -> ApiException.notFound("Item not found"));
    }

    private static void apply(Item i, ItemInput in) {
        i.setName(in.name().trim());
        i.setCode(blankToNull(in.code()));
        i.setBarcode(blankToNull(in.barcode()));
        i.setHsn(blankToNull(in.hsn()));
        i.setUnit(in.unit() == null || in.unit().isBlank() ? "NOS" : in.unit().trim().toUpperCase());
        i.setRatePaise(in.ratePaise());
        i.setGstRateBp(in.gstRateBp());
        i.setStockMilli(in.stockMilli());
        i.setReorderLevelMilli(in.reorderLevelMilli() == null ? 0 : in.reorderLevelMilli());
        i.setTaxInclusive(Boolean.TRUE.equals(in.taxInclusive()));
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v.trim();
    }
}
