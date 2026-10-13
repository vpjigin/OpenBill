package app.billing.purchase;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import app.billing.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "purchase")
public class Purchase extends BaseEntity {

    @Column(name = "supplier_id", nullable = false, updatable = false, length = 36)
    private UUID supplierId;

    @Column(name = "supplier_name", nullable = false, updatable = false)
    private String supplierName;

    @Column(name = "supplier_gstin", updatable = false)
    private String supplierGstin;

    @Column(name = "bill_no", nullable = false, updatable = false)
    private String billNo;

    @Column(name = "bill_date", nullable = false, updatable = false)
    private Instant billDate;

    @Column(name = "inter_state", nullable = false, updatable = false)
    private boolean interState;

    @Column(name = "taxable_paise", nullable = false, updatable = false)
    private long taxablePaise;

    @Column(name = "cgst_paise", nullable = false, updatable = false)
    private long cgstPaise;

    @Column(name = "sgst_paise", nullable = false, updatable = false)
    private long sgstPaise;

    @Column(name = "igst_paise", nullable = false, updatable = false)
    private long igstPaise;

    @Column(name = "round_off_paise", nullable = false, updatable = false)
    private long roundOffPaise;

    @Column(name = "total_paise", nullable = false, updatable = false)
    private long totalPaise;

    private String notes;

    @Column(name = "created_by", nullable = false, updatable = false, length = 36)
    private UUID createdBy;

    @OneToMany(mappedBy = "purchase", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<PurchaseLine> lines = new ArrayList<>();

    public void addLine(PurchaseLine line) {
        line.setPurchase(this);
        lines.add(line);
    }

    public UUID getSupplierId() { return supplierId; }
    public void setSupplierId(UUID supplierId) { this.supplierId = supplierId; }
    public String getSupplierName() { return supplierName; }
    public void setSupplierName(String supplierName) { this.supplierName = supplierName; }
    public String getSupplierGstin() { return supplierGstin; }
    public void setSupplierGstin(String supplierGstin) { this.supplierGstin = supplierGstin; }
    public String getBillNo() { return billNo; }
    public void setBillNo(String billNo) { this.billNo = billNo; }
    public Instant getBillDate() { return billDate; }
    public void setBillDate(Instant billDate) { this.billDate = billDate; }
    public boolean isInterState() { return interState; }
    public void setInterState(boolean interState) { this.interState = interState; }
    public long getTaxablePaise() { return taxablePaise; }
    public void setTaxablePaise(long taxablePaise) { this.taxablePaise = taxablePaise; }
    public long getCgstPaise() { return cgstPaise; }
    public void setCgstPaise(long cgstPaise) { this.cgstPaise = cgstPaise; }
    public long getSgstPaise() { return sgstPaise; }
    public void setSgstPaise(long sgstPaise) { this.sgstPaise = sgstPaise; }
    public long getIgstPaise() { return igstPaise; }
    public void setIgstPaise(long igstPaise) { this.igstPaise = igstPaise; }
    public long getRoundOffPaise() { return roundOffPaise; }
    public void setRoundOffPaise(long roundOffPaise) { this.roundOffPaise = roundOffPaise; }
    public long getTotalPaise() { return totalPaise; }
    public void setTotalPaise(long totalPaise) { this.totalPaise = totalPaise; }
    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public List<PurchaseLine> getLines() { return lines; }
}
