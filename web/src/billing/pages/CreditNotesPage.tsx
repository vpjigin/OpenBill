import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { formatPaise } from "../../shared/money";
import { ErrorNote, PageHeader, errorText } from "../../shared/ui";
import { billingApi, type CreditNote } from "../billingApi";

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

export function CreditNotesPage() {
  const [from, setFrom] = useState(() => {
    const d = new Date();
    d.setDate(d.getDate() - 30);
    return d.toISOString().slice(0, 10);
  });
  const [to, setTo] = useState(todayIso);
  const [rows, setRows] = useState<CreditNote[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    billingApi
      .creditNotes({ from, to, limit: 200 })
      .then(setRows)
      .catch((e) => setError(errorText(e)));
  }, [from, to]);

  return (
    <div className="flex flex-col gap-4">
      <PageHeader title="Credit notes" subtitle="Returns and refunds" />
      {error && <ErrorNote message={error} />}
      <div className="flex flex-wrap items-end gap-3 text-sm">
        <label className="flex flex-col gap-1">
          <span className="text-muted">From</span>
          <input type="date" className="rounded-lg border border-line px-2 py-1.5" value={from} onChange={(e) => setFrom(e.target.value)} />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-muted">To</span>
          <input type="date" className="rounded-lg border border-line px-2 py-1.5" value={to} onChange={(e) => setTo(e.target.value)} />
        </label>
      </div>
      <div className="overflow-x-auto rounded-xl border border-line bg-surface">
        <table className="w-full min-w-[520px] border-collapse text-sm">
          <thead>
            <tr className="text-left text-xs uppercase tracking-wider text-muted">
              <th className="px-4 py-2.5">Credit note</th>
              <th className="px-2 py-2.5">Date</th>
              <th className="px-2 py-2.5">Invoice</th>
              <th className="px-4 py-2.5 text-right">Total</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={4} className="px-4 py-6 text-center text-muted">
                  No credit notes in this range
                </td>
              </tr>
            ) : (
              rows.map((cn) => (
                <tr key={cn.id} className="border-t border-line-soft">
                  <td className="px-4 py-2.5">
                    <Link to={`/credit-notes/${cn.id}`} className="font-medium text-brand">
                      {cn.cnNo}
                    </Link>
                  </td>
                  <td className="px-2 py-2.5 text-muted">{new Date(cn.cnDate).toLocaleString("en-IN")}</td>
                  <td className="px-2 py-2.5">
                    <Link to={`/invoices/${cn.invoiceId}`} className="text-brand">
                      {cn.invoiceNo}
                    </Link>
                  </td>
                  <td className="px-4 py-2.5 text-right font-mono">{formatPaise(cn.totalPaise)}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </div>
  );
}
