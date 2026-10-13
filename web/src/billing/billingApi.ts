import { api } from "../shared/api";

export interface Item {
  id: string;
  name: string;
  code: string | null;
  barcode: string | null;
  hsn: string | null;
  unit: string;
  ratePaise: number;
  costPaise: number;
  gstRateBp: number;
  stockMilli: number;
  reorderLevelMilli: number;
  taxInclusive: boolean;
  version: number;
}

export type PaymentMode = "CASH" | "UPI" | "CARD" | "CREDIT";

export type RefundMode = "CASH" | "UPI" | "CARD" | "CREDIT";

export interface InvoiceLine {
  id?: string;
  lineNo: number;
  name: string;
  hsn: string | null;
  qtyMilli: number;
  unit: string;
  taxInclusive: boolean;
  ratePaise: number;
  gstRateBp: number;
  grossPaise: number;
  discountPaise: number;
  taxablePaise: number;
  cgstPaise: number;
  sgstPaise: number;
  igstPaise: number;
  totalPaise: number;
  returnableQtyMilli?: number;
}

export interface CreditNoteBrief {
  id: string;
  cnNo: string;
  cnDate: string;
  totalPaise: number;
}

export interface CreditNoteLine {
  lineNo: number;
  invoiceLineId: string;
  name: string;
  hsn: string | null;
  qtyMilli: number;
  unit: string;
  ratePaise: number;
  gstRateBp: number;
  taxablePaise: number;
  cgstPaise: number;
  sgstPaise: number;
  igstPaise: number;
  totalPaise: number;
}

export interface CreditNote {
  id: string;
  cnNo: string;
  cnDate: string;
  invoiceId: string;
  invoiceNo: string;
  customerId: string | null;
  customerName: string | null;
  customerGstin: string | null;
  customerStateCode: string | null;
  interState: boolean;
  reason: string;
  refundMode: RefundMode;
  taxablePaise: number;
  cgstPaise: number;
  sgstPaise: number;
  igstPaise: number;
  roundOffPaise: number;
  totalPaise: number;
  lines: CreditNoteLine[];
}

export interface Invoice {
  id: string;
  invoiceNo: string;
  invoiceDate: string;
  customerId: string | null;
  customerName: string | null;
  customerPhone: string | null;
  customerGstin: string | null;
  customerStateCode: string | null;
  customerAddress: string | null;
  interState: boolean;
  paymentMode: PaymentMode;
  grossPaise: number;
  discountPaise: number;
  taxablePaise: number;
  cgstPaise: number;
  sgstPaise: number;
  igstPaise: number;
  roundOffPaise: number;
  totalPaise: number;
  billDiscountPaise: number;
  irn: string | null;
  ackNo: string | null;
  ackDate: string | null;
  signedQr: string | null;
  createdByName: string | null;
  lines: InvoiceLine[];
  creditNotes?: CreditNoteBrief[];
}

export interface Supplier {
  id: string;
  name: string;
  phone: string | null;
  gstin: string | null;
  stateCode: string | null;
  address: string | null;
  version: number;
}

export interface PurchaseLine {
  lineNo: number;
  itemId: string;
  name: string;
  hsn: string | null;
  unit: string;
  qtyMilli: number;
  costPaise: number;
  gstRateBp: number;
  taxablePaise: number;
  cgstPaise: number;
  sgstPaise: number;
  igstPaise: number;
  totalPaise: number;
}

export interface PurchaseSummary {
  id: string;
  supplierId: string;
  supplierName: string;
  billNo: string;
  billDate: string;
  totalPaise: number;
}

export interface Purchase {
  id: string;
  supplierId: string;
  supplierName: string;
  supplierGstin: string | null;
  billNo: string;
  billDate: string;
  interState: boolean;
  taxablePaise: number;
  cgstPaise: number;
  sgstPaise: number;
  igstPaise: number;
  roundOffPaise: number;
  totalPaise: number;
  notes: string | null;
  createdByName: string | null;
  lines: PurchaseLine[];
  cancelled: boolean;
}

export interface Customer {
  id: string;
  name: string;
  phone: string | null;
  gstin: string | null;
  stateCode: string | null;
  address: string | null;
  email: string | null;
  pincode: string | null;
  version: number;
}

export interface PageResult<T> {
  items: T[];
  total: number;
  page: number;
  size: number;
}

export interface ShopInfo {
  appName: string;
  shopName: string;
  gstin: string | null;
  stateCode: string | null;
  stateName: string | null;
  address: string | null;
  phone: string | null;
  hasLogo: boolean;
  receiptShowLogo: boolean;
  /** Authenticated blob URL for print preview; revoke when discarding. */
  logoObjectUrl?: string | null;
}

export const billingApi = {
  shop: () => api<ShopInfo>("/api/shop"),
  customers: (params?: { q?: string; page?: number; size?: number }) => {
    const sp = new URLSearchParams();
    if (params?.q?.trim()) sp.set("q", params.q.trim());
    sp.set("page", String(params?.page ?? 0));
    sp.set("size", String(params?.size ?? 50));
    return api<PageResult<Customer>>(`/api/customers?${sp}`);
  },
  customer: (id: string) => api<Customer>(`/api/customers/${id}`),
  createCustomer: (body: unknown) => api<Customer>("/api/customers", { method: "POST", json: body }),
  updateCustomer: (id: string, body: unknown) =>
    api<Customer>(`/api/customers/${id}`, { method: "PUT", json: body }),
  suppliers: (params?: { q?: string; page?: number; size?: number }) => {
    const sp = new URLSearchParams();
    if (params?.q?.trim()) sp.set("q", params.q.trim());
    sp.set("page", String(params?.page ?? 0));
    sp.set("size", String(params?.size ?? 50));
    return api<PageResult<Supplier>>(`/api/suppliers?${sp}`);
  },
  createSupplier: (body: unknown) => api<Supplier>("/api/suppliers", { method: "POST", json: body }),
  updateSupplier: (id: string, body: unknown) =>
    api<Supplier>(`/api/suppliers/${id}`, { method: "PUT", json: body }),
  purchases: (params?: { from?: string; to?: string; supplierId?: string }) => {
    const sp = new URLSearchParams();
    if (params?.from) sp.set("from", params.from);
    if (params?.to) sp.set("to", params.to);
    if (params?.supplierId) sp.set("supplierId", params.supplierId);
    const qs = sp.toString();
    return api<PurchaseSummary[]>(`/api/purchases${qs ? `?${qs}` : ""}`);
  },
  purchase: (id: string) => api<Purchase>(`/api/purchases/${id}`),
  createPurchase: (body: unknown) => api<Purchase>("/api/purchases", { method: "POST", json: body }),
  cancelPurchase: (id: string) => api<void>(`/api/purchases/${id}/cancel`, { method: "POST" }),
  items: (params?: { q?: string; page?: number; size?: number }) => {
    const sp = new URLSearchParams();
    if (params?.q?.trim()) sp.set("q", params.q.trim());
    sp.set("page", String(params?.page ?? 0));
    sp.set("size", String(params?.size ?? 50));
    return api<PageResult<Item>>(`/api/items?${sp}`);
  },
  itemsAll: async (params?: { q?: string }) => {
    const size = 200;
    let page = 0;
    const all: Item[] = [];
    for (;;) {
      const r = await billingApi.items({ ...params, page, size });
      all.push(...r.items);
      if (all.length >= r.total) break;
      page += 1;
    }
    return all;
  },
  lowStockItems: () => api<Item[]>("/api/items/low-stock"),
  createItem: (body: unknown) => api<Item>("/api/items", { method: "POST", json: body }),
  updateItem: (id: string, body: unknown) => api<Item>(`/api/items/${id}`, { method: "PUT", json: body }),
  generateItemBarcode: (id: string) => api<Item>(`/api/items/${id}/barcode`, { method: "POST" }),
  deleteItem: (id: string) => api<void>(`/api/items/${id}`, { method: "DELETE" }),
  createInvoice: (body: unknown) => api<Invoice>("/api/invoices", { method: "POST", json: body }),
  invoices: (params?: { from?: string; to?: string; q?: string; limit?: number }) => {
    const sp = new URLSearchParams();
    if (params?.from) sp.set("from", params.from);
    if (params?.to) sp.set("to", params.to);
    if (params?.q) sp.set("q", params.q);
    sp.set("limit", String(params?.limit ?? 100));
    const qs = sp.toString();
    return api<Invoice[]>(`/api/invoices?${qs}`);
  },
  invoice: (id: string) => api<Invoice>(`/api/invoices/${id}`),
  createCreditNote: (body: unknown) => api<CreditNote>("/api/credit-notes", { method: "POST", json: body }),
  creditNotes: (params?: { from?: string; to?: string; limit?: number }) => {
    const sp = new URLSearchParams();
    if (params?.from) sp.set("from", params.from);
    if (params?.to) sp.set("to", params.to);
    sp.set("limit", String(params?.limit ?? 100));
    const qs = sp.toString();
    return api<CreditNote[]>(`/api/credit-notes?${qs}`);
  },
  creditNote: (id: string) => api<CreditNote>(`/api/credit-notes/${id}`),
  heldBills: () => api<HeldBillSummary[]>("/api/held-bills"),
  heldBill: (id: string) => api<HeldBillDetail>(`/api/held-bills/${id}`),
  createHeldBill: (body: { label?: string | null; payload: string }) =>
    api<HeldBillSummary>("/api/held-bills", { method: "POST", json: body }),
  deleteHeldBill: (id: string) => api<void>(`/api/held-bills/${id}`, { method: "DELETE" }),
  printStatus: () =>
    api<{ configured: boolean; defaultPrinterName: string | null; paperMm: number }>("/api/print/status"),
  printInvoice: (id: string) => api<void>(`/api/print/invoice/${id}`, { method: "POST" }),
  printCreditNote: (id: string) => api<void>(`/api/print/credit-note/${id}`, { method: "POST" }),
  printDayEnd: (date: string) =>
    api<void>(`/api/print/day-end?date=${encodeURIComponent(date)}`, { method: "POST" }),
  openCashDrawer: () => api<void>("/api/print/drawer", { method: "POST" }),
  einvoiceExport: (from: string, to: string) =>
    api<{ problems: string[]; documents: unknown[] | null }>(
      `/api/einvoice/json?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
    ),
  saveInvoiceIrn: (
    id: string,
    body: { irn: string; ackNo: string; ackDate: string; signedQr: string },
  ) => api<{ id: string; irn: string; ackNo: string; ackDate: string; signedQr: string }>(
    `/api/einvoice/${id}/irn`,
    { method: "POST", json: body },
  ),
};

export interface HeldBillSummary {
  id: string;
  label: string | null;
  itemCount: number;
  previewTotalPaise: number | null;
  createdByName: string;
  createdAt: string;
}

export interface HeldBillDetail {
  id: string;
  label: string | null;
  payload: string;
  createdByName: string;
  createdAt: string;
}
