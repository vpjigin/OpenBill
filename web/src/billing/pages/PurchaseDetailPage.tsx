import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { StatGrid, StatCard } from "../../shared/layout";
import { formatGstRate, formatPaise, formatQty } from "../../shared/money";
import { Button, ErrorNote, PageHeader, errorText } from "../../shared/ui";
import { billingApi, type Purchase } from "../billingApi";

export function PurchaseDetailPage() {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [purchase, setPurchase] = useState<Purchase | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    billingApi.purchase(id).then(setPurchase).catch((e) => setError(errorText(e)));
  }, [id]);

  async function cancel() {
    if (!purchase || !window.confirm("Cancel this purchase and reverse stock? You can re-enter the bill later.")) {
      return;
    }
    setError(null);
    try {
      await billingApi.cancelPurchase(purchase.id);
      navigate("/purchases");
    } catch (e) {
      setError(errorText(e));
    }
  }

  if (!purchase) {
    return (
      <>
        <PageHeader title="Purchase" />
        <ErrorNote message={error} />
      </>
    );
  }

  const inputGst = purchase.cgstPaise + purchase.sgstPaise + purchase.igstPaise;

  return (
    <div className="flex w-full min-w-0 flex-col gap-4">
      <PageHeader
        title={purchase.billNo}
        subtitle={`${purchase.supplierName} · ${purchase.billDate}`}
        actions={
          !purchase.cancelled && (
            <Button variant="danger" className="min-h-11" onClick={() => void cancel()}>
              Cancel purchase
            </Button>
          )
        }
      />
      <ErrorNote message={error} />
      {purchase.cancelled && (
        <p className="rounded-lg border border-amber-500/40 bg-amber-500/10 px-4 py-2 text-sm">Cancelled</p>
      )}
      <div className="text-sm text-muted">
        {purchase.interState ? "Inter-state" : "Intra-state"} · Recorded by {purchase.createdByName ?? "—"}
      </div>
      <StatGrid>
        <StatCard label="Taxable" value={formatPaise(purchase.taxablePaise)} />
        <StatCard label="Input GST" value={formatPaise(inputGst)} />
        <StatCard label="Total" value={formatPaise(purchase.totalPaise)} />
      </StatGrid>
      <div className="flex flex-col gap-2 md:hidden">
        {purchase.lines.map((l) => (
          <div key={l.lineNo} className="rounded-xl border border-line bg-surface p-4 text-sm">
            <div className="flex justify-between gap-2 font-medium">
              <span>{l.name}</span>
              <span className="font-mono">{formatPaise(l.totalPaise)}</span>
            </div>
            <dl className="mt-2 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-xs text-muted">
              <dt>Qty</dt>
              <dd className="m-0 text-right font-mono text-ink">{formatQty(l.qtyMilli, l.unit)}</dd>
              <dt>Cost</dt>
              <dd className="m-0 text-right font-mono text-ink">{formatPaise(l.costPaise)}</dd>
              <dt>GST</dt>
              <dd className="m-0 text-right">{formatGstRate(l.gstRateBp)}</dd>
            </dl>
          </div>
        ))}
      </div>
      <div className="hidden rounded-xl border border-line bg-surface md:block">
        <table className="w-full border-collapse text-sm">
          <thead>
            <tr className="text-left text-xs uppercase tracking-wider text-muted">
              <th className="px-4 py-2">Item</th>
              <th className="px-2 py-2 text-right">Qty</th>
              <th className="px-2 py-2 text-right">Cost</th>
              <th className="px-2 py-2">GST</th>
              <th className="px-4 py-2 text-right">Total</th>
            </tr>
          </thead>
          <tbody>
            {purchase.lines.map((l) => (
              <tr key={l.lineNo} className="border-t border-line-soft">
                <td className="px-4 py-2">{l.name}</td>
                <td className="px-2 py-2 text-right font-mono">{formatQty(l.qtyMilli, l.unit)}</td>
                <td className="px-2 py-2 text-right font-mono">{formatPaise(l.costPaise)}</td>
                <td className="px-2 py-2">{formatGstRate(l.gstRateBp)}</td>
                <td className="px-4 py-2 text-right font-mono">{formatPaise(l.totalPaise)}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="mt-2">
        <Link to="/purchases" className="text-brand">
          ← All purchases
        </Link>
      </p>
    </div>
  );
}
