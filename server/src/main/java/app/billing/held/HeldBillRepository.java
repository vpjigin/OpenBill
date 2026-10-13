package app.billing.held;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface HeldBillRepository extends JpaRepository<HeldBill, UUID> {

    @Query("select h from HeldBill h where h.deletedAt is null order by h.createdAt desc")
    List<HeldBill> findActiveNewestFirst();

    Optional<HeldBill> findByIdAndDeletedAtIsNull(UUID id);

    @Modifying
    @Query("update HeldBill h set h.deletedAt = :now where h.deletedAt is null and h.createdAt < :cutoff")
    int softDeleteOlderThan(Instant cutoff, Instant now);
}
