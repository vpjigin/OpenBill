import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { formatPaise } from "../../shared/money";
import { Card, PageHeader } from "../../shared/ui";
import { billingApi, type Invoice } from "../billingApi";

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

function monthStartIso(): string {
  const d = new Date();
  return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0, 10);
}

export function InvoicesPage() {
  const navigate = useNavigate();
  const [rows, setRows] = useState<Invoice[]>([]);
  const [from, setFrom] = useState(monthStartIso());
  const [to, setTo] = useState(todayIso());
  const [q, setQ] = useState("");

  const load = useCallback(() => {
    billingApi
      .invoices({ from: from || undefined, to: to || undefined, q: q.trim() || undefined, limit: 100 })
      .then(setRows)
      .catch(() => setRows([]));
  }, [from, to, q]);

  useEffect(() => {
    const t = setTimeout(load, 200);
    return () => clearTimeout(t);
  }, [load]);

  return (
    <>
      <PageHeader title="Invoices" subtitle="Search and reprint saved bills" />
      <Card
        actions={
          <div className="flex flex-wrap items-center gap-2">
            <input
              type="date"
              aria-label="From date"
              value={from}
              onChange={(e) => setFrom(e.target.value)}
              className="min-h-10 rounded-lg border border-[#b9c2bd] px-2 text-sm"
            />
            <span className="text-muted">to</span>
            <input
              type="date"
              aria-label="To date"
              value={to}
              onChange={(e) => setTo(e.target.value)}
              className="min-h-10 rounded-lg border border-[#b9c2bd] px-2 text-sm"
            />
            <input
              type="search"
              aria-label="Search invoices"
              placeholder="Invoice no, customer…"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              className="min-h-10 w-48 rounded-lg border border-[#b9c2bd] px-3 text-sm"
            />
          </div>
        }
      >
        <div className="overflow-x-auto">
          <table className="w-full min-w-[600px] border-collapse text-sm">
            <thead>
              <tr className="text-left text-xs uppercase tracking-wider text-muted">
                <th className="px-5 py-2.5 font-medium">Invoice</th>
                <th className="px-3 py-2.5 font-medium">Date</th>
                <th className="px-3 py-2.5 font-medium">Customer</th>
                <th className="px-3 py-2.5 font-medium">Payment</th>
                <th className="px-5 py-2.5 text-right font-medium">Total</th>
              </tr>
            </thead>
            <tbody>
              {rows.length === 0 && (
                <tr className="border-t border-line-soft">
                  <td colSpan={5} className="px-5 py-4 text-muted">
                    No invoices in this range.
                  </td>
                </tr>
              )}
              {rows.map((i) => (
                <tr
                  key={i.id}
                  className="cursor-pointer border-t border-line-soft hover:bg-brand-soft/40"
                  onClick={() => navigate(`/invoices/${i.id}`)}
                >
                  <td className="px-5 py-3 font-mono text-[13px]">{i.invoiceNo}</td>
                  <td className="px-3 py-3">
                    {new Date(i.invoiceDate).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" })}
                  </td>
                  <td className="px-3 py-3">{i.customerName ?? "Walk-in"}</td>
                  <td className="px-3 py-3">{i.paymentMode}</td>
                  <td className="px-5 py-3 text-right font-mono">{formatPaise(i.totalPaise)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </>
  );
}
