package app.billing.invoice;

import java.util.UUID;

import app.billing.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** A snapshot of the item at billing time: later price/name edits never change old invoices. */
@Entity
@Table(name = "invoice_line")
public class InvoiceLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "invoice_id", nullable = false)
    private Invoice invoice;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "item_id", length = 36)
    private UUID itemId;

    @Column(nullable = false)
    private String name;

    private String hsn;

    @Column(name = "qty_milli", nullable = false)
    private long qtyMilli;

    @Column(nullable = false)
    private String unit = "NOS";

    @Column(name = "tax_inclusive", nullable = false)
    private boolean taxInclusive;

    @Column(name = "rate_paise", nullable = false)
    private long ratePaise;

    @Column(name = "gst_rate_bp", nullable = false)
    private int gstRateBp;

    @Column(name = "gross_paise", nullable = false)
    private long grossPaise;

    @Column(name = "discount_paise", nullable = false)
    private long discountPaise;

    @Column(name = "taxable_paise", nullable = false)
    private long taxablePaise;

    @Column(name = "cgst_paise", nullable = false)
    private long cgstPaise;

    @Column(name = "sgst_paise", nullable = false)
    private long sgstPaise;

    @Column(name = "igst_paise", nullable = false)
    private long igstPaise;

    @Column(name = "total_paise", nullable = false)
    private long totalPaise;

    public Invoice getInvoice() { return invoice; }
    void setInvoice(Invoice invoice) { this.invoice = invoice; }
    public int getLineNo() { return lineNo; }
    public void setLineNo(int lineNo) { this.lineNo = lineNo; }
    public UUID getItemId() { return itemId; }
    public void setItemId(UUID itemId) { this.itemId = itemId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getHsn() { return hsn; }
    public void setHsn(String hsn) { this.hsn = hsn; }
    public long getQtyMilli() { return qtyMilli; }
    public void setQtyMilli(long qtyMilli) { this.qtyMilli = qtyMilli; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public boolean isTaxInclusive() { return taxInclusive; }
    public void setTaxInclusive(boolean taxInclusive) { this.taxInclusive = taxInclusive; }
    public long getRatePaise() { return ratePaise; }
    public void setRatePaise(long ratePaise) { this.ratePaise = ratePaise; }
    public int getGstRateBp() { return gstRateBp; }
    public void setGstRateBp(int gstRateBp) { this.gstRateBp = gstRateBp; }
    public long getGrossPaise() { return grossPaise; }
    public void setGrossPaise(long grossPaise) { this.grossPaise = grossPaise; }
    public long getDiscountPaise() { return discountPaise; }
    public void setDiscountPaise(long discountPaise) { this.discountPaise = discountPaise; }
    public long getTaxablePaise() { return taxablePaise; }
    public void setTaxablePaise(long taxablePaise) { this.taxablePaise = taxablePaise; }
    public long getCgstPaise() { return cgstPaise; }
    public void setCgstPaise(long cgstPaise) { this.cgstPaise = cgstPaise; }
    public long getSgstPaise() { return sgstPaise; }
    public void setSgstPaise(long sgstPaise) { this.sgstPaise = sgstPaise; }
    public long getIgstPaise() { return igstPaise; }
    public void setIgstPaise(long igstPaise) { this.igstPaise = igstPaise; }
    public long getTotalPaise() { return totalPaise; }
    public void setTotalPaise(long totalPaise) { this.totalPaise = totalPaise; }
}
