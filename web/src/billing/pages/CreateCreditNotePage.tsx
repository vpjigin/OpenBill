import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { formatQty, parseQty } from "../../shared/money";
import { Button, ErrorNote, PageHeader, errorText } from "../../shared/ui";
import { billingApi, type Invoice, type RefundMode } from "../billingApi";

type LineDraft = { invoiceLineId: string; name: string; unit: string; returnable: number; qty: string };

export function CreateCreditNotePage() {
  const { id: invoiceId } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const [invoice, setInvoice] = useState<Invoice | null>(null);
  const [lines, setLines] = useState<LineDraft[]>([]);
  const [reason, setReason] = useState("");
  const [refundMode, setRefundMode] = useState<RefundMode>("CASH");
  const [error, setError] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);

  useEffect(() => {
    if (!invoiceId) return;
    billingApi
      .invoice(invoiceId)
      .then((inv) => {
        setInvoice(inv);
        setLines(
          inv.lines
            .filter((l) => l.id && (l.returnableQtyMilli ?? 0) > 0)
            .map((l) => ({
              invoiceLineId: l.id!,
              name: l.name,
              unit: l.unit,
              returnable: l.returnableQtyMilli ?? 0,
              qty: formatQty(l.returnableQtyMilli ?? 0, l.unit),
            })),
        );
      })
      .catch((e) => setError(errorText(e)));
  }, [invoiceId]);

  const hasReturn = useMemo(
    () => lines.some((l) => (parseQty(l.qty, l.unit) ?? 0) > 0),
    [lines],
  );

  async function save() {
    if (!invoiceId || !reason.trim()) {
      setError("Reason is required");
      return;
    }
    const returnLines = lines.flatMap((l) => {
      const qtyMilli = parseQty(l.qty, l.unit) ?? 0;
      return qtyMilli > 0 ? [{ invoiceLineId: l.invoiceLineId, qtyMilli }] : [];
    });
    const payload = {
      invoiceId,
      reason: reason.trim(),
      refundMode,
      lines: returnLines,
    };
    if (payload.lines.length === 0) {
      setError("Enter a return quantity for at least one line");
      return;
    }
    setSaving(true);
    setError(null);
    try {
      const cn = await billingApi.createCreditNote(payload);
      navigate(`/credit-notes/${cn.id}`);
    } catch (e) {
      setError(errorText(e));
    } finally {
      setSaving(false);
    }
  }

  if (error && !invoice) {
    return (
      <>
        <PageHeader title="Credit note" />
        <ErrorNote message={error} />
        <Link to="/invoices" className="text-sm text-brand">
          ← Invoices
        </Link>
      </>
    );
  }

  if (!invoice) {
    return <PageHeader title="Credit note" subtitle="Loading…" />;
  }

  if (lines.length === 0) {
    return (
      <>
        <PageHeader title="Credit note" subtitle={invoice.invoiceNo} />
        <p className="text-sm text-muted">Nothing left to return on this invoice.</p>
        <Link to={`/invoices/${invoice.id}`} className="text-sm text-brand">
          ← Back to invoice
        </Link>
      </>
    );
  }

  return (
    <div className="flex max-w-2xl flex-col gap-4">
      <PageHeader title="Create credit note" subtitle={`Against ${invoice.invoiceNo}`} />
      {error && <ErrorNote message={error} />}
      <div className="overflow-x-auto rounded-xl border border-line bg-surface">
        <table className="w-full min-w-[480px] border-collapse text-sm">
          <thead>
            <tr className="text-left text-xs uppercase tracking-wider text-muted">
              <th className="px-4 py-2.5">Item</th>
              <th className="px-2 py-2.5 text-right">Returnable</th>
              <th className="px-4 py-2.5 text-right">Return qty</th>
            </tr>
          </thead>
          <tbody>
            {lines.map((l, i) => (
              <tr key={l.invoiceLineId} className="border-t border-line-soft">
                <td className="px-4 py-2.5">{l.name}</td>
                <td className="px-2 py-2.5 text-right font-mono text-muted">
                  {formatQty(l.returnable, l.unit)} {l.unit}
                </td>
                <td className="px-4 py-2.5 text-right">
                  <input
                    className="w-28 rounded-md border border-line px-2 py-1 text-right font-mono"
                    value={l.qty}
                    onChange={(e) => {
                      const next = [...lines];
                      next[i] = { ...l, qty: e.target.value };
                      setLines(next);
                    }}
                  />
                </td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <label className="flex flex-col gap-1 text-sm">
        <span className="font-medium">Reason</span>
        <input
          className="rounded-lg border border-line px-3 py-2"
          value={reason}
          onChange={(e) => setReason(e.target.value)}
          required
        />
      </label>
      <label className="flex flex-col gap-1 text-sm">
        <span className="font-medium">Refund mode</span>
        <select
          className="rounded-lg border border-line px-3 py-2"
          value={refundMode}
          onChange={(e) => setRefundMode(e.target.value as RefundMode)}
        >
          <option value="CASH">Cash</option>
          <option value="UPI">UPI</option>
          <option value="CARD">Card</option>
          <option value="CREDIT">Store credit</option>
        </select>
      </label>
      <div className="flex flex-wrap gap-2">
        <Button variant="primary" disabled={saving || !hasReturn || !reason.trim()} onClick={() => void save()}>
          {saving ? "Saving…" : "Save credit note"}
        </Button>
        <Link to={`/invoices/${invoice.id}`} className="inline-flex items-center text-sm text-brand">
          Cancel
        </Link>
      </div>
    </div>
  );
}
