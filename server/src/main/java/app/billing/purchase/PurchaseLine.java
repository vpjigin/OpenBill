package app.billing.purchase;

import java.util.UUID;

import app.billing.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "purchase_line")
public class PurchaseLine extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "purchase_id", nullable = false, updatable = false)
    private Purchase purchase;

    @Column(name = "line_no", nullable = false, updatable = false)
    private int lineNo;

    @Column(name = "item_id", updatable = false, length = 36)
    private UUID itemId;

    @Column(nullable = false, updatable = false)
    private String name;

    private String hsn;

    @Column(nullable = false, updatable = false)
    private String unit;

    @Column(name = "qty_milli", nullable = false, updatable = false)
    private long qtyMilli;

    @Column(name = "cost_paise", nullable = false, updatable = false)
    private long costPaise;

    @Column(name = "previous_cost_paise", nullable = false, updatable = false)
    private long previousCostPaise;

    @Column(name = "gst_rate_bp", nullable = false, updatable = false)
    private int gstRateBp;

    @Column(name = "taxable_paise", nullable = false, updatable = false)
    private long taxablePaise;

    @Column(name = "cgst_paise", nullable = false, updatable = false)
    private long cgstPaise;

    @Column(name = "sgst_paise", nullable = false, updatable = false)
    private long sgstPaise;

    @Column(name = "igst_paise", nullable = false, updatable = false)
    private long igstPaise;

    @Column(name = "total_paise", nullable = false, updatable = false)
    private long totalPaise;

    public void setPurchase(Purchase purchase) {
        this.purchase = purchase;
    }

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
    public long getCostPaise() { return costPaise; }
    public void setCostPaise(long costPaise) { this.costPaise = costPaise; }
    public long getPreviousCostPaise() { return previousCostPaise; }
    public void setPreviousCostPaise(long previousCostPaise) { this.previousCostPaise = previousCostPaise; }
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
