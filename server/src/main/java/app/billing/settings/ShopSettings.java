package app.billing.settings;

import app.billing.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** One row: the shop's details and what the admin last switched on. */
@Entity
@Table(name = "shop_settings")
public class ShopSettings extends BaseEntity {

    @Column(name = "shop_name", nullable = false)
    private String shopName;

    private String gstin;

    @Column(name = "state_code")
    private String stateCode;

    private String address;

    private String phone;

    private String pincode;

    @Column(name = "invoice_prefix", nullable = false)
    private String invoicePrefix = "INV";

    @Column(name = "lan_enabled", nullable = false)
    private boolean lanEnabled;

    @Column(name = "public_enabled", nullable = false)
    private boolean publicEnabled;

    @Column(name = "tunnel_token")
    private String tunnelToken;

    @Column(name = "public_url")
    private String publicUrl;

    /** Base64-encoded PNG/JPEG, max 200 KB at upload time. */
    @Column(name = "logo")
    private String logo;

    @Column(name = "logo_mime")
    private String logoMime;

    @Column(name = "receipt_show_logo", nullable = false)
    private boolean receiptShowLogo = true;

    public String getShopName() { return shopName; }
    public void setShopName(String shopName) { this.shopName = shopName; }
    public String getGstin() { return gstin; }
    public void setGstin(String gstin) { this.gstin = gstin; }
    public String getStateCode() { return stateCode; }
    public void setStateCode(String stateCode) { this.stateCode = stateCode; }
    public String getAddress() { return address; }
    public void setAddress(String address) { this.address = address; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getPincode() { return pincode; }
    public void setPincode(String pincode) { this.pincode = pincode; }
    public String getInvoicePrefix() { return invoicePrefix; }
    public void setInvoicePrefix(String invoicePrefix) { this.invoicePrefix = invoicePrefix; }
    public boolean isLanEnabled() { return lanEnabled; }
    public void setLanEnabled(boolean lanEnabled) { this.lanEnabled = lanEnabled; }
    public boolean isPublicEnabled() { return publicEnabled; }
    public void setPublicEnabled(boolean publicEnabled) { this.publicEnabled = publicEnabled; }
    public String getTunnelToken() { return tunnelToken; }
    public void setTunnelToken(String tunnelToken) { this.tunnelToken = tunnelToken; }
    public String getPublicUrl() { return publicUrl; }
    public void setPublicUrl(String publicUrl) { this.publicUrl = publicUrl; }
    public String getLogo() { return logo; }
    public void setLogo(String logo) { this.logo = logo; }
    public String getLogoMime() { return logoMime; }
    public void setLogoMime(String logoMime) { this.logoMime = logoMime; }
    public boolean isReceiptShowLogo() { return receiptShowLogo; }
    public void setReceiptShowLogo(boolean receiptShowLogo) { this.receiptShowLogo = receiptShowLogo; }
}
