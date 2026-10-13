package app.billing.channel;

/** Where a request came in. Decided by the port/address it arrived on, never by headers. */
public enum Channel {
    /** Desktop admin window: 127.0.0.1:adminPort */
    ADMIN,
    /** Billing website opened on the same computer: 127.0.0.1:billingPort */
    LOCAL,
    /** Billing website from another device on the shop Wi-Fi: lanIp:billingPort */
    LAN,
    /** Billing website through the public tunnel: 127.0.0.1:publicPort (only cloudflared connects here) */
    PUBLIC;

    public static final String REQUEST_ATTR = "app.channel";

    public boolean isRemote() {
        return this == LAN || this == PUBLIC;
    }
}
