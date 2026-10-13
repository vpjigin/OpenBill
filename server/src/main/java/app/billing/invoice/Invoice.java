package app.billing.invoice;

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

/**
 * Immutable once saved: there is deliberately no update endpoint.
 * Corrections go through credit notes (to be added), as GST rules expect.
 */
@Entity
@Table(name = "invoice")
public class Invoice extends BaseEntity {

    @Column(name = "invoice_no", nullable = false, unique = true, updatable = false)
    private String invoiceNo;

    @Column(nullable = false, updatable = false)
    private String fy;

    @Column(nullable = false, updatable = false)
    private long seq;

    @Column(name = "invoice_date", nullable = false, updatable = false)
    private Instant invoiceDate;

    @Column(name = "customer_name")
    private String customerName;

    @Column(name = "customer_phone")
    private String customerPhone;

    @Column(name = "customer_gstin")
    private String customerGstin;

    @Column(name = "customer_id", length = 36, updatable = false)
    private UUID customerId;

    @Column(name = "customer_state_code", updatable = false)
    private String customerStateCode;

    @Column(name = "customer_address", updatable = false)
    private String customerAddress;

    @Column(name = "inter_state", nullable = false)
    private boolean interState;

    @Enumerated(EnumType.STRING)
    @Column(name = "payment_mode", nullable = false)
    private PaymentMode paymentMode;

    @Column(name = "gross_paise", nullable = false)
    private long grossPaise;

    @Column(name = "discount_paise", nullable = false)
    private long discountPaise;

    @Column(name = "bill_discount_paise", nullable = false)
    private long billDiscountPaise;

    @Column(name = "taxable_paise", nullable = false)
    private long taxablePaise;

    @Column(name = "cgst_paise", nullable = false)
    private long cgstPaise;

    @Column(name = "sgst_paise", nullable = false)
    private long sgstPaise;

    @Column(name = "igst_paise", nullable = false)
    private long igstPaise;

    @Column(name = "round_off_paise", nullable = false)
    private long roundOffPaise;

    @Column(name = "total_paise", nullable = false)
    private long totalPaise;

    @Column(name = "created_by", nullable = false, updatable = false, length = 36)
    private UUID createdBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Channel channel;

    /** Set only via {@link app.billing.einvoice.InvoiceIrnService} after portal upload. */
    @Column(name = "irn")
    private String irn;

    @Column(name = "ack_no")
    private String ackNo;

    @Column(name = "ack_date")
    private String ackDate;

    @Column(name = "signed_qr")
    private String signedQr;

    @OneToMany(mappedBy = "invoice", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("lineNo")
    private List<InvoiceLine> lines = new ArrayList<>();

    public void addLine(InvoiceLine line) {
        line.setInvoice(this);
        lines.add(line);
    }

    public String getInvoiceNo() { return invoiceNo; }
    public void setInvoiceNo(String invoiceNo) { this.invoiceNo = invoiceNo; }
    public String getFy() { return fy; }
    public void setFy(String fy) { this.fy = fy; }
    public long getSeq() { return seq; }
    public void setSeq(long seq) { this.seq = seq; }
    public Instant getInvoiceDate() { return invoiceDate; }
    public void setInvoiceDate(Instant invoiceDate) { this.invoiceDate = invoiceDate; }
    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }
    public String getCustomerPhone() { return customerPhone; }
    public void setCustomerPhone(String customerPhone) { this.customerPhone = customerPhone; }
    public String getCustomerGstin() { return customerGstin; }
    public void setCustomerGstin(String customerGstin) { this.customerGstin = customerGstin; }
    public UUID getCustomerId() { return customerId; }
    public void setCustomerId(UUID customerId) { this.customerId = customerId; }
    public String getCustomerStateCode() { return customerStateCode; }
    public void setCustomerStateCode(String customerStateCode) { this.customerStateCode = customerStateCode; }
    public String getCustomerAddress() { return customerAddress; }
    public void setCustomerAddress(String customerAddress) { this.customerAddress = customerAddress; }
    public boolean isInterState() { return interState; }
    public void setInterState(boolean interState) { this.interState = interState; }
    public PaymentMode getPaymentMode() { return paymentMode; }
    public void setPaymentMode(PaymentMode paymentMode) { this.paymentMode = paymentMode; }
    public long getGrossPaise() { return grossPaise; }
    public void setGrossPaise(long grossPaise) { this.grossPaise = grossPaise; }
    public long getDiscountPaise() { return discountPaise; }
    public void setDiscountPaise(long discountPaise) { this.discountPaise = discountPaise; }
    public long getBillDiscountPaise() { return billDiscountPaise; }
    public void setBillDiscountPaise(long billDiscountPaise) { this.billDiscountPaise = billDiscountPaise; }
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
    public List<InvoiceLine> getLines() { return lines; }
    public String getIrn() { return irn; }
    public void setIrn(String irn) { this.irn = irn; }
    public String getAckNo() { return ackNo; }
    public void setAckNo(String ackNo) { this.ackNo = ackNo; }
    public String getAckDate() { return ackDate; }
    public void setAckDate(String ackDate) { this.ackDate = ackDate; }
    public String getSignedQr() { return signedQr; }
    public void setSignedQr(String signedQr) { this.signedQr = signedQr; }
}
