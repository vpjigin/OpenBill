package app.billing.customer;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    @Query("""
            select c from Customer c
            where c.deletedAt is null
              and (lower(c.name) like lower(concat('%', :q, '%'))
                   or c.phone like concat(:q, '%')
                   or upper(c.gstin) = upper(:q))
            order by c.name""")
    List<Customer> search(String q, Pageable pageable);

    Optional<Customer> findByIdAndDeletedAtIsNull(UUID id);
}
