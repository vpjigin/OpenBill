package app.billing.settings;

import java.util.Optional;
import java.util.function.Consumer;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SettingsService {

    private final ShopSettingsRepository repo;

    public SettingsService(ShopSettingsRepository repo) {
        this.repo = repo;
    }

    @Transactional(readOnly = true)
    public Optional<ShopSettings> find() {
        return repo.findFirstByOrderByCreatedAtAsc();
    }

    @Transactional(readOnly = true)
    public ShopSettings get() {
        return find().orElseThrow(() -> new IllegalStateException("Shop is not set up yet"));
    }

    @Transactional
    public ShopSettings update(Consumer<ShopSettings> change) {
        ShopSettings s = get();
        change.accept(s);
        return repo.save(s);
    }

    @Transactional
    public ShopSettings create(ShopSettings s) {
        return repo.save(s);
    }
}
