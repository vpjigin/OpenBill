import { useEffect, useMemo, useState } from "react";
import { Link, useNavigate, useParams } from "react-router-dom";
import { DataTable, FormPageWrap, StickyActionBar } from "../../shared/layout";
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
    <FormPageWrap>
      <div className="flex w-full min-w-0 flex-col gap-4">
        <PageHeader title="Create credit note" subtitle={`Against ${invoice.invoiceNo}`} />
        {error && <ErrorNote message={error} />}
        <div className="rounded-xl border border-line bg-surface p-2 sm:p-4">
          <DataTable
            columns={[
              { key: "item", header: "Item", mobilePrimary: true, cell: (l) => l.name },
              {
                key: "ret",
                header: "Returnable",
                className: "text-right font-mono text-muted",
                cell: (l) => `${formatQty(l.returnable, l.unit)} ${l.unit}`,
              },
              {
                key: "qty",
                header: "Return qty",
                className: "text-right",
                cell: (l) => {
                  const i = lines.findIndex((x) => x.invoiceLineId === l.invoiceLineId);
                  return (
                    <input
                      className="min-h-11 w-full max-w-[8rem] rounded-md border border-line px-2 text-right font-mono text-base sm:text-sm"
                      value={l.qty}
                      onClick={(e) => e.stopPropagation()}
                      onChange={(e) => {
                        const next = [...lines];
                        next[i] = { ...l, qty: e.target.value };
                        setLines(next);
                      }}
                    />
                  );
                },
              },
            ]}
            rows={lines}
            rowKey={(l) => l.invoiceLineId}
            emptyMessage="No returnable lines"
          />
        </div>
        <label className="flex flex-col gap-1 text-sm">
          <span className="font-medium">Reason</span>
          <input
            className="min-h-11 rounded-lg border border-line px-3 text-base sm:text-sm"
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            required
          />
        </label>
        <label className="flex flex-col gap-1 text-sm">
          <span className="font-medium">Refund mode</span>
          <select
            className="min-h-11 rounded-lg border border-line px-3 text-base sm:text-sm"
            value={refundMode}
            onChange={(e) => setRefundMode(e.target.value as RefundMode)}
          >
            <option value="CASH">Cash</option>
            <option value="UPI">UPI</option>
            <option value="CARD">Card</option>
            <option value="CREDIT">Store credit</option>
          </select>
        </label>
        <StickyActionBar>
          <div className="flex w-full min-w-0 flex-wrap gap-2">
            <Button
              variant="primary"
              className="min-h-11 flex-1 sm:flex-none"
              disabled={saving || !hasReturn || !reason.trim()}
              onClick={() => void save()}
            >
              {saving ? "Saving…" : "Save credit note"}
            </Button>
            <Link to={`/invoices/${invoice.id}`} className="inline-flex min-h-11 items-center text-sm text-brand">
              Cancel
            </Link>
          </div>
        </StickyActionBar>
      </div>
    </FormPageWrap>
  );
}
