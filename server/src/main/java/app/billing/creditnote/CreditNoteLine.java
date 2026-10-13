package app.billing.creditnote;

import java.util.UUID;

import app.billing.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "credit_note_line")
public class CreditNoteLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_note_id", nullable = false)
    private CreditNote creditNote;

    @Column(name = "invoice_line_id", nullable = false, length = 36, updatable = false)
    private UUID invoiceLineId;

    @Column(name = "line_no", nullable = false)
    private int lineNo;

    @Column(name = "item_id", length = 36)
    private UUID itemId;

    @Column(nullable = false)
    private String name;

    private String hsn;

    @Column(nullable = false)
    private String unit;

    @Column(name = "qty_milli", nullable = false)
    private long qtyMilli;

    @Column(name = "rate_paise", nullable = false)
    private long ratePaise;

    @Column(name = "gst_rate_bp", nullable = false)
    private int gstRateBp;

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

    public CreditNote getCreditNote() { return creditNote; }
    void setCreditNote(CreditNote creditNote) { this.creditNote = creditNote; }
    public UUID getInvoiceLineId() { return invoiceLineId; }
    public void setInvoiceLineId(UUID invoiceLineId) { this.invoiceLineId = invoiceLineId; }
    public int getLineNo() { return lineNo; }
    public void setLineNo(int lineNo) { this.lineNo = lineNo; }
    public UUID getItemId() { return itemId; }
    public void setItemId(UUID itemId) { this.itemId = itemId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getHsn() { return hsn; }
    public void setHsn(String hsn) { this.hsn = hsn; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public long getQtyMilli() { return qtyMilli; }
    public void setQtyMilli(long qtyMilli) { this.qtyMilli = qtyMilli; }
    public long getRatePaise() { return ratePaise; }
    public void setRatePaise(long ratePaise) { this.ratePaise = ratePaise; }
    public int getGstRateBp() { return gstRateBp; }
    public void setGstRateBp(int gstRateBp) { this.gstRateBp = gstRateBp; }
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
