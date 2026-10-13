package app.billing.supplier;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface SupplierRepository extends JpaRepository<Supplier, UUID> {

    @Query("""
            select s from Supplier s
            where s.deletedAt is null
              and (lower(s.name) like lower(concat('%', :q, '%'))
                   or s.phone like concat(:q, '%')
                   or upper(s.gstin) = upper(:q))
            order by s.name""")
    List<Supplier> search(String q, Pageable pageable);

    Optional<Supplier> findByIdAndDeletedAtIsNull(UUID id);
}
