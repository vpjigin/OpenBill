package app.billing.creditnote;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import app.billing.channel.Channel;
import app.billing.common.BaseEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

@Entity
@Table(name = "credit_note")
public class CreditNote extends BaseEntity {

    @Column(name = "cn_no", nullable = false, unique = true, updatable = false)
    private String cnNo;

    @Column(nullable = false, updatable = false)
    private String fy;

    @Column(nullable = false, updatable = false)
    private long seq;

    @Column(name = "cn_date", nullable = false, updatable = false)
    private Instant cnDate;

    @Column(name = "invoice_id", nullable = false, updatable = false, length = 36)
    private UUID invoiceId;

    @Column(name = "invoice_no", nullable = false, updatable = false)
    private String invoiceNo;

    @Column(name = "customer_id", length = 36, updatable = false)
    private UUID customerId;

    @Column(name = "customer_name", updatable = false)
    private String customerName;

    @Column(name = "customer_gstin", updatable = false)
    private String customerGstin;

    @Column(name = "customer_state_code", updatable = false)
    private String customerStateCode;

    @Column(name = "inter_state", nullable = false, updatable = false)
    private boolean interState;

    @Column(nullable = false, updatable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "refund_mode", nullable = false, updatable = false)
    private RefundMode refundMode;

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

    @Column(name = "created_by", nullable = false, updatable = false, length = 36)
    private UUID createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Channel channel;

    @OneToMany(mappedBy = "creditNote", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<CreditNoteLine> lines = new ArrayList<>();

    public void addLine(CreditNoteLine line) {
        line.setCreditNote(this);
        lines.add(line);
    }

    public String getCnNo() { return cnNo; }
    public void setCnNo(String cnNo) { this.cnNo = cnNo; }
    public String getFy() { return fy; }
    public void setFy(String fy) { this.fy = fy; }
    public long getSeq() { return seq; }
    public void setSeq(long seq) { this.seq = seq; }
    public Instant getCnDate() { return cnDate; }
    public void setCnDate(Instant cnDate) { this.cnDate = cnDate; }
    public UUID getInvoiceId() { return invoiceId; }
    public void setInvoiceId(UUID invoiceId) { this.invoiceId = invoiceId; }
    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerGstin() { return customerGstin; }
    public void setCustomerGstin(String customerGstin) { this.customerGstin = customerGstin; }
    public String getCustomerStateCode() { return customerStateCode; }
    public void setCustomerStateCode(String customerStateCode) { this.customerStateCode = customerStateCode; }
    public boolean isInterState() { return interState; }
    public void setInterState(boolean interState) { this.interState = interState; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public RefundMode getRefundMode() { return refundMode; }
    public void setRefundMode(RefundMode refundMode) { this.refundMode = refundMode; }
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
    public UUID getCreatedBy() { return createdBy; }
    public void setCreatedBy(UUID createdBy) { this.createdBy = createdBy; }
    public Channel getChannel() { return channel; }
    public void setChannel(Channel channel) { this.channel = channel; }
    public List<CreditNoteLine> getLines() { return lines; }
}
