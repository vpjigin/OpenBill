package app.billing.invoice;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface InvoiceRepository extends JpaRepository<Invoice, UUID> {

    @Query("select i from Invoice i where i.deletedAt is null order by i.invoiceDate desc")
    List<Invoice> recent(Pageable page);

    Optional<Invoice> findByIdAndDeletedAtIsNull(UUID id);

    @Query("""
            select i from Invoice i
            where i.deletedAt is null
              and (:from is null or i.invoiceDate >= :from)
              and (:toExclusive is null or i.invoiceDate < :toExclusive)
              and (:q is null or lower(i.invoiceNo) like lower(concat('%', :q, '%'))
                   or lower(i.customerName) like lower(concat('%', :q, '%'))
                   or i.customerPhone like concat(:q, '%'))
            order by i.invoiceDate desc""")
    List<Invoice> search(Instant from, Instant toExclusive, String q, Pageable page);

    @Query("select count(i) from Invoice i where i.deletedAt is null and i.invoiceDate >= :from")
    long countSince(Instant from);

    @Query("select coalesce(sum(i.totalPaise), 0) from Invoice i where i.deletedAt is null and i.invoiceDate >= :from")
    long totalSince(Instant from);

    @Query("""
            select distinct i from Invoice i
            left join fetch i.lines
            where i.deletedAt is null
              and i.customerGstin is not null and trim(i.customerGstin) <> ''
              and (i.irn is null or trim(i.irn) = '')
              and i.invoiceDate >= :from
              and i.invoiceDate < :toExclusive
            order by i.invoiceDate asc, i.invoiceNo asc""")
    List<Invoice> findB2bWithoutIrn(Instant from, Instant toExclusive, Pageable page);
}
