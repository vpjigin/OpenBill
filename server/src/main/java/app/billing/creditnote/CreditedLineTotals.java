package app.billing.creditnote;

import java.util.UUID;

/** Amounts already returned on an invoice line via prior credit notes. */
public record CreditedLineTotals(
        UUID invoiceLineId,
        long qtyMilli,
        long taxablePaise,
        long cgstPaise,
        long sgstPaise,
        long igstPaise,
        long totalPaise) {
}
