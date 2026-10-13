package app.billing.supplier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    @Query("""
            select count(s) from Supplier s
            where s.deletedAt is null
              and (:q is null or (lower(s.name) like lower(concat('%', :q, '%'))
                   or s.phone like concat(:q, '%')
                   or upper(s.gstin) = upper(:q)))
            """)
    long countSearch(@Param("q") String q);

    @Query("""
            select s from Supplier s
            where s.deletedAt is null
              and (:q is null or (lower(s.name) like lower(concat('%', :q, '%'))
                   or s.phone like concat(:q, '%')
                   or upper(s.gstin) = upper(:q)))
            order by s.name""")
    List<Supplier> search(@Param("q") String q, Pageable pageable);

    Optional<Supplier> findByIdAndDeletedAtIsNull(UUID id);
}
