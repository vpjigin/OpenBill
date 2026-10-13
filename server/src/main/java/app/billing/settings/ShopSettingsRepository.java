package app.billing.settings;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ShopSettingsRepository extends JpaRepository<ShopSettings, UUID> {

    Optional<ShopSettings> findFirstByOrderByCreatedAtAsc();
}
