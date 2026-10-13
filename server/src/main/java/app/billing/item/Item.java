package app.billing.item;

import app.billing.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

@Entity
@Table(name = "item")
public class Item extends BaseEntity {

    @Column(nullable = false)
    private String name;

    private String code;

    private String barcode;

    private String hsn;

    @Column(nullable = false)
    private String unit = "NOS";

    /** Selling price in paise (before GST unless {@link #taxInclusive}). */
    @Column(name = "rate_paise", nullable = false)
    private long ratePaise;

    /** Last purchase cost in paise (before GST). */
    @Column(name = "cost_paise", nullable = false)
    private long costPaise;

    /** GST rate in basis points: 5% = 500, 12% = 1200, 18% = 1800. */
    @Column(name = "gst_rate_bp", nullable = false)
    private int gstRateBp;

    @Column(name = "stock_milli", nullable = false)
    private long stockMilli;

    @Column(name = "reorder_level_milli", nullable = false)
    private long reorderLevelMilli;

    @Column(name = "tax_inclusive", nullable = false)
    private boolean taxInclusive;

    @Column(nullable = false)
    private boolean active = true;

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getBarcode() { return barcode; }
    public void setBarcode(String barcode) { this.barcode = barcode; }
    public String getHsn() { return hsn; }
    public void setHsn(String hsn) { this.hsn = hsn; }
    public String getUnit() { return unit; }
    public void setUnit(String unit) { this.unit = unit; }
    public long getRatePaise() { return ratePaise; }
    public void setRatePaise(long ratePaise) { this.ratePaise = ratePaise; }
    public long getCostPaise() { return costPaise; }
    public void setCostPaise(long costPaise) { this.costPaise = costPaise; }
    public int getGstRateBp() { return gstRateBp; }
    public void setGstRateBp(int gstRateBp) { this.gstRateBp = gstRateBp; }
    public long getStockMilli() { return stockMilli; }
    public void setStockMilli(long stockMilli) { this.stockMilli = stockMilli; }
    public long getReorderLevelMilli() { return reorderLevelMilli; }
    public void setReorderLevelMilli(long reorderLevelMilli) { this.reorderLevelMilli = reorderLevelMilli; }
    public boolean isTaxInclusive() { return taxInclusive; }
    public void setTaxInclusive(boolean taxInclusive) { this.taxInclusive = taxInclusive; }
    public boolean isActive() { return active; }
    public void setActive(boolean active) { this.active = active; }
}
