package app.billing.print;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PrinterRepository extends JpaRepository<Printer, UUID> {

    @Query("select p from Printer p where p.deletedAt is null order by p.name")
    List<Printer> findAllActive();

    @Query("select p from Printer p where p.deletedAt is null and p.defaultPrinter = true")
    Optional<Printer> findDefault();
}
