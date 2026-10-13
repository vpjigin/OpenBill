import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
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

  return (
    <>
      <PageHeader
        title={purchase.billNo}
        subtitle={`${purchase.supplierName} · ${purchase.billDate}`}
        actions={
          !purchase.cancelled && (
            <Button variant="danger" onClick={() => void cancel()}>
              Cancel purchase
            </Button>
          )
        }
      />
      <ErrorNote message={error} />
      {purchase.cancelled && (
        <p className="mb-4 rounded-lg border border-amber-500/40 bg-amber-500/10 px-4 py-2 text-sm">Cancelled</p>
      )}
      <div className="mb-4 text-sm text-muted">
        {purchase.interState ? "Inter-state" : "Intra-state"} · Recorded by {purchase.createdByName ?? "—"}
      </div>
      <div className="overflow-x-auto rounded-xl border border-line bg-surface">
        <table className="w-full min-w-[640px] border-collapse text-sm">
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
      <dl className="mt-4 grid max-w-sm grid-cols-[1fr_auto] gap-x-4 gap-y-1 text-sm">
        <dt>Taxable</dt>
        <dd className="m-0 font-mono">{formatPaise(purchase.taxablePaise)}</dd>
        <dt>Input GST</dt>
        <dd className="m-0 font-mono">
          {formatPaise(purchase.cgstPaise + purchase.sgstPaise + purchase.igstPaise)}
        </dd>
        <dt>Total</dt>
        <dd className="m-0 font-mono font-semibold">{formatPaise(purchase.totalPaise)}</dd>
      </dl>
      <p className="mt-6">
        <Link to="/purchases" className="text-brand">
          ← All purchases
        </Link>
      </p>
    </>
  );
}
