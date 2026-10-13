package app.billing.creditnote;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface CreditNoteRepository extends JpaRepository<CreditNote, UUID> {

    Optional<CreditNote> findByIdAndDeletedAtIsNull(UUID id);

    @Query("""
            select cn from CreditNote cn
            where cn.deletedAt is null
              and (:from is null or cn.cnDate >= :from)
              and (:toExclusive is null or cn.cnDate < :toExclusive)
            order by cn.cnDate desc""")
    List<CreditNote> search(Instant from, Instant toExclusive, Pageable page);

    @Query("""
            select cn from CreditNote cn
            where cn.deletedAt is null and cn.invoiceId = :invoiceId
            order by cn.cnDate asc""")
    List<CreditNote> findByInvoiceIdOrderByCnDateAsc(UUID invoiceId);

    @Query("""
            select distinct cn from CreditNote cn
            left join fetch cn.lines
            where cn.deletedAt is null
              and cn.customerGstin is not null and trim(cn.customerGstin) <> ''
              and cn.cnDate >= :from
              and cn.cnDate < :toExclusive
            order by cn.cnDate asc, cn.cnNo asc""")
    List<CreditNote> findB2bInRange(Instant from, Instant toExclusive, Pageable page);
}
