import { useCallback, useEffect, useState } from "react";
import { Link } from "react-router-dom";
import { formatPaise } from "../../shared/money";
import { Button, ErrorNote, PageHeader, errorText } from "../../shared/ui";
import { billingApi, type PurchaseSummary } from "../billingApi";

function defaultRange() {
  const to = new Date();
  const from = new Date();
  from.setMonth(from.getMonth() - 1);
  return { from: from.toISOString().slice(0, 10), to: to.toISOString().slice(0, 10) };
}

export function PurchasesPage() {
  const [rows, setRows] = useState<PurchaseSummary[]>([]);
  const [range, setRange] = useState(defaultRange);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    billingApi
      .purchases({ from: range.from, to: range.to })
      .then(setRows)
      .catch((e) => setError(errorText(e)));
  }, [range.from, range.to]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <>
      <PageHeader
        title="Purchases"
        subtitle="Stock in from supplier bills"
        actions={
          <Link to="/purchases/new">
            <Button>New purchase</Button>
          </Link>
        }
      />
      <ErrorNote message={error} />
      <div className="mb-4 flex flex-wrap items-end gap-3">
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-muted">From</span>
          <input
            type="date"
            className="rounded-lg border border-line px-2 py-1.5"
            value={range.from}
            onChange={(e) => setRange((r) => ({ ...r, from: e.target.value }))}
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-muted">To</span>
          <input
            type="date"
            className="rounded-lg border border-line px-2 py-1.5"
            value={range.to}
            onChange={(e) => setRange((r) => ({ ...r, to: e.target.value }))}
          />
        </label>
        <Button variant="secondary" onClick={load}>
          Refresh
        </Button>
      </div>
      <div className="overflow-x-auto rounded-xl border border-line bg-surface">
        <table className="w-full min-w-[520px] border-collapse text-sm">
          <thead>
            <tr className="text-left text-xs uppercase tracking-wider text-muted">
              <th className="px-4 py-2">Date</th>
              <th className="px-2 py-2">Supplier</th>
              <th className="px-2 py-2">Bill no</th>
              <th className="px-4 py-2 text-right">Total</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={4} className="px-4 py-8 text-center text-muted">
                  No purchases in this range
                </td>
              </tr>
            ) : (
              rows.map((p) => (
                <tr key={p.id} className="border-t border-line-soft">
                  <td className="px-4 py-2">{p.billDate}</td>
                  <td className="px-2 py-2">{p.supplierName}</td>
                  <td className="px-2 py-2">
                    <Link to={`/purchases/${p.id}`} className="font-mono text-brand">
                      {p.billNo}
                    </Link>
                  </td>
                  <td className="px-4 py-2 text-right font-mono">{formatPaise(p.totalPaise)}</td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </>
  );
}
