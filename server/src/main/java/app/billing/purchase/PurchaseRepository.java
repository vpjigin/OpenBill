package app.billing.purchase;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PurchaseRepository extends JpaRepository<Purchase, UUID> {

    boolean existsBySupplierIdAndBillNoAndDeletedAtIsNull(UUID supplierId, String billNo);

    Optional<Purchase> findByIdAndDeletedAtIsNull(UUID id);

    @Query("""
            select p from Purchase p
            where p.deletedAt is null
              and p.billDate >= :from and p.billDate < :toExclusive
              and (:supplierId is null or p.supplierId = :supplierId)
            order by p.billDate desc, p.createdAt desc""")
    List<Purchase> search(Instant from, Instant toExclusive, UUID supplierId);

}
