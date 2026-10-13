import { useEffect, useMemo, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { gstStateName } from "../../shared/gstStates";
import { formatGstRate, formatPaise, formatQty } from "../../shared/money";
import { ErrorNote, PageHeader, errorText } from "../../shared/ui";
import { ReceiptPrintPanel } from "../ReceiptPrintPanel";
import { PrintDocument, creditNoteToPrintInvoice } from "../PrintDocument";
import { useReceiptPrintMode } from "../useReceiptPrintMode";
import { billingApi, type CreditNote } from "../billingApi";
import { usePrintLayout } from "../usePrintLayout";
import { useShop } from "../useShop";

export function CreditNoteDetailPage() {
  const { id } = useParams<{ id: string }>();
  const shop = useShop();
  const { layout, setLayout } = usePrintLayout();
  const { mode } = useReceiptPrintMode();
  const [serverPrintReady, setServerPrintReady] = useState(false);
  const [cn, setCn] = useState<CreditNote | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    billingApi
      .creditNote(id)
      .then(setCn)
      .catch((e) => setError(errorText(e)));
  }, [id]);

  useEffect(() => {
    billingApi.printStatus().then((s) => setServerPrintReady(s.configured)).catch(() => setServerPrintReady(false));
  }, []);

  const printInvoice = useMemo(() => (cn ? creditNoteToPrintInvoice(cn) : null), [cn]);

  if (error) {
    return (
      <>
        <PageHeader title="Credit note" />
        <ErrorNote message={error} />
        <Link to="/credit-notes" className="text-sm text-brand">
          ← Credit notes
        </Link>
      </>
    );
  }

  if (!cn || !printInvoice) {
    return <PageHeader title="Credit note" subtitle="Loading…" />;
  }

  const placeLabel = cn.interState
    ? `Other state${cn.customerStateCode ? ` — ${gstStateName(cn.customerStateCode)}` : ""}`
    : `Within state${cn.customerStateCode ? ` — ${gstStateName(cn.customerStateCode)}` : ""}`;

  const showBrowserPrint = mode === "browser" || !serverPrintReady;

  return (
    <div className="flex flex-col gap-6">
      <ReceiptPrintPanel kind="credit-note" id={cn.id} />
      <div className="no-print">
        <PageHeader title={cn.cnNo} subtitle={new Date(cn.cnDate).toLocaleString("en-IN")} />
        <p className="text-sm">
          Against invoice{" "}
          <Link to={`/invoices/${cn.invoiceId}`} className="text-brand">
            {cn.invoiceNo}
          </Link>
        </p>
        <p className="text-sm text-muted">Reason: {cn.reason}</p>
        <div className="mb-4 grid gap-4 rounded-xl border border-line bg-surface p-5 text-sm md:grid-cols-2">
          <div>
            <h2 className="m-0 mb-2 text-xs font-semibold uppercase tracking-wide text-muted">Customer</h2>
            <p className="m-0 font-medium">{cn.customerName ?? "Walk-in"}</p>
            {cn.customerGstin && <p className="m-0 font-mono text-[13px]">{cn.customerGstin}</p>}
          </div>
          <div>
            <dl className="m-0 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1">
              <dt className="text-muted">Place of supply</dt>
              <dd className="m-0">{placeLabel}</dd>
              <dt className="text-muted">Refund</dt>
              <dd className="m-0">{cn.refundMode}</dd>
            </dl>
          </div>
        </div>
        <div className="overflow-x-auto rounded-xl border border-line bg-surface">
          <table className="w-full min-w-[640px] border-collapse text-sm">
            <thead>
              <tr className="text-left text-xs uppercase tracking-wider text-muted">
                <th className="px-4 py-2.5">#</th>
                <th className="px-2 py-2.5">Item</th>
                <th className="px-2 py-2.5 text-right">Qty</th>
                <th className="px-2 py-2.5 text-right">Taxable</th>
                <th className="px-2 py-2.5 text-right">GST</th>
                <th className="px-4 py-2.5 text-right">Total</th>
              </tr>
            </thead>
            <tbody>
              {cn.lines.map((l) => (
                <tr key={l.lineNo} className="border-t border-line-soft">
                  <td className="px-4 py-2.5 text-muted">{l.lineNo}</td>
                  <td className="px-2 py-2.5">{l.name}</td>
                  <td className="px-2 py-2.5 text-right font-mono">
                    {formatQty(l.qtyMilli, l.unit)} {l.unit}
                  </td>
                  <td className="px-2 py-2.5 text-right font-mono">{formatPaise(l.taxablePaise)}</td>
                  <td className="px-2 py-2.5 text-right">{formatGstRate(l.gstRateBp)}</td>
                  <td className="px-4 py-2.5 text-right font-mono font-medium">{formatPaise(l.totalPaise)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <p className="mt-4">
          <Link to="/credit-notes" className="text-sm text-brand">
            ← All credit notes
          </Link>
        </p>
      </div>

      {showBrowserPrint && (
        <PrintDocument
          invoice={printInvoice}
          shop={shop}
          layout={layout}
          onLayoutChange={setLayout}
          documentTitle="CREDIT NOTE"
          againstLabel={`Against invoice ${cn.invoiceNo}`}
          reason={cn.reason}
          paymentLabel={`Refund via ${cn.refundMode}`}
        />
      )}
    </div>
  );
}
