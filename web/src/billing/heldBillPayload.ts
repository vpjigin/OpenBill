import type { DiscountMode } from "./gst";
import type { PaymentMode } from "./billingApi";

export interface HeldBillPayloadV1 {
  v: 1;
  lines: Array<{
    itemId: string;
    qtyMilli: number;
    discount: { value: string; mode: DiscountMode };
  }>;
  billDiscount: { value: string; mode: DiscountMode };
  interState: boolean;
  payment: PaymentMode;
  selectedCustomerId: string | null;
  inlineCustomer: {
    name: string;
    phone: string;
    gstin: string;
    stateCode: string;
    address: string;
  } | null;
  showNewCustomer: boolean;
  saveCustomer: boolean;
  previewTotalPaise: number;
}

export function parseHeldBillPayload(raw: string): HeldBillPayloadV1 | null {
  try {
    const p = JSON.parse(raw) as HeldBillPayloadV1;
    if (p.v !== 1 || !Array.isArray(p.lines)) return null;
    return p;
  } catch {
    return null;
  }
}
