package app.billing.customer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    @Query("""
            select count(c) from Customer c
            where c.deletedAt is null
              and (:q is null or (lower(c.name) like lower(concat('%', :q, '%'))
                   or c.phone like concat(:q, '%')
                   or upper(c.gstin) = upper(:q)))
            """)
    long countSearch(@Param("q") String q);

    @Query("""
            select c from Customer c
            where c.deletedAt is null
              and (:q is null or (lower(c.name) like lower(concat('%', :q, '%'))
                   or c.phone like concat(:q, '%')
                   or upper(c.gstin) = upper(:q)))
            order by c.name""")
    List<Customer> search(@Param("q") String q, Pageable pageable);

    Optional<Customer> findByIdAndDeletedAtIsNull(UUID id);
}
