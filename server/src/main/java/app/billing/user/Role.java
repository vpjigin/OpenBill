package app.billing.user;

public enum Role {
    /** Everything, including users, server access and backup. The desktop admin app needs this role. */
    ADMIN,
    /** Billing plus items, stock and reports. */
    MANAGER,
    /** Billing only. */
    CASHIER
}
