import { api } from "../shared/api";

export interface ReportTotals {
  billsCount: number;
  grossPaise: number;
  discountPaise: number;
  taxablePaise: number;
  cgstPaise: number;
  sgstPaise: number;
  igstPaise: number;
  roundOffPaise: number;
  totalPaise: number;
}

export interface ModeTotal {
  mode: string;
  totalPaise: number;
}

export interface CreditByMode {
  mode: string;
  count: number;
  totalPaise: number;
}

export interface ModeNet {
  mode: string;
  salesPaise: number;
  refundPaise: number;
  netPaise: number;
}

export interface PurchaseTotals {
  count: number;
  taxablePaise: number;
  cgstPaise: number;
  sgstPaise: number;
  igstPaise: number;
  roundOffPaise: number;
  totalPaise: number;
}

export interface DayEndReport {
  date: string;
  totals: ReportTotals;
  purchases: PurchaseTotals;
  salesByMode: ModeTotal[];
  creditNotesByMode: CreditByMode[];
  netByMode: ModeNet[];
  firstInvoiceNo: string | null;
  lastInvoiceNo: string | null;
}

export interface DailySalesRow {
  date: string;
  totals: ReportTotals;
  purchases: PurchaseTotals;
}

export interface HsnRow {
  hsn: string;
  gstRateBp: number;
  unit: string;
  qtyMilli: number;
  taxablePaise: number;
  cgstPaise: number;
  sgstPaise: number;
  igstPaise: number;
  totalPaise: number;
}

const TOKEN_KEY = "billing.token";

function readToken(): string | null {
  try {
    return localStorage.getItem(TOKEN_KEY);
  } catch {
    return null;
  }
}

export const reportsApi = {
  dayEnd: (date: string) => api<DayEndReport>(`/api/reports/day-end?date=${encodeURIComponent(date)}`),
  sales: (from: string, to: string) =>
    api<DailySalesRow[]>(
      `/api/reports/sales?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`,
    ),
  hsn: (from: string, to: string) =>
    api<HsnRow[]>(`/api/reports/hsn?from=${encodeURIComponent(from)}&to=${encodeURIComponent(to)}`),
  async downloadGstr1(month: string): Promise<{ blob: Blob; filename: string }> {
    const token = readToken();
    const res = await fetch(`/api/reports/gstr1?month=${encodeURIComponent(month)}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!res.ok) {
      let message = `Request failed (${res.status})`;
      try {
        const data = await res.json();
        if (data?.error) message = data.error;
      } catch {
        /* not JSON */
      }
      throw new Error(message);
    }
    const blob = await res.blob();
    const disp = res.headers.get("Content-Disposition") ?? "";
    const match = /filename="([^"]+)"/.exec(disp);
    const filename = match?.[1] ?? `GSTR1-${month}.zip`;
    return { blob, filename };
  },
};

export function csvEscape(value: string | number): string {
  const s = String(value);
  if (/[",\n\r]/.test(s)) {
    return `"${s.replace(/"/g, '""')}"`;
  }
  return s;
}
