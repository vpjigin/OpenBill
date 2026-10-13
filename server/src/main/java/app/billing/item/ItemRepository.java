package app.billing.item;

import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ItemRepository extends JpaRepository<Item, UUID> {

    boolean existsByBarcodeAndDeletedAtIsNull(String barcode);

    @Query(value = """
            select coalesce(max(cast(substr(barcode, 4, 9) as integer)), 0) from item
            where deleted_at is null and barcode is not null and length(barcode) = 13
              and barcode glob '200[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]'
            """, nativeQuery = true)
    long maxInternalBarcodeSequence();

    @Query("""
            select count(i) from Item i
            where i.deletedAt is null and i.active = true
              and (:q is null or lower(i.name) like lower(concat('%', :q, '%'))
                   or i.code = :q or i.barcode = :q)
            """)
    long countSearch(@Param("q") String q);

    @Query("""
            select i from Item i
            where i.deletedAt is null and i.active = true
              and (:q is null or lower(i.name) like lower(concat('%', :q, '%'))
                   or i.code = :q or i.barcode = :q)
            order by i.name""")
    List<Item> search(@Param("q") String q, Pageable pageable);

    @Query("""
            select count(i) from Item i
            where i.deletedAt is null and i.active = true
              and i.reorderLevelMilli > 0 and i.stockMilli <= i.reorderLevelMilli""")
    long countLowStock();

    @Query("""
            select i from Item i
            where i.deletedAt is null and i.active = true
              and i.reorderLevelMilli > 0 and i.stockMilli <= i.reorderLevelMilli
            order by i.name""")
    List<Item> findLowStock();
}
