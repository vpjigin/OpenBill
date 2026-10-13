import { useEffect, useState, type FormEvent } from "react";
import { Link, useLocation, useNavigate, useParams } from "react-router-dom";
import { useSession } from "../../shared/useSession";
import { gstStateName } from "../../shared/gstStates";
import { formatGstRate, formatPaise, formatQty } from "../../shared/money";
import { Button, ErrorNote, PageHeader, errorText } from "../../shared/ui";
import { ReceiptPrintPanel } from "../ReceiptPrintPanel";
import { PrintDocument } from "../PrintDocument";
import { useReceiptPrintMode } from "../useReceiptPrintMode";
import { billingApi, type Invoice } from "../billingApi";
import { usePrintLayout } from "../usePrintLayout";
import { useShop } from "../useShop";

export function InvoiceDetailPage() {
  const { id } = useParams<{ id: string }>();
  const location = useLocation();
  const autoPrint = Boolean((location.state as { autoPrint?: boolean } | null)?.autoPrint);
  const navigate = useNavigate();
  const { mode } = useReceiptPrintMode();
  const [serverPrintReady, setServerPrintReady] = useState(false);
  const { user } = useSession();
  const canManage = user?.role === "ADMIN" || user?.role === "MANAGER";
  const shop = useShop();
  const { layout, setLayout } = usePrintLayout();
  const [invoice, setInvoice] = useState<Invoice | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [irnForm, setIrnForm] = useState({ irn: "", ackNo: "", ackDate: "", signedQr: "" });
  const [irnBusy, setIrnBusy] = useState(false);
  const [irnMsg, setIrnMsg] = useState<string | null>(null);

  useEffect(() => {
    if (!id) return;
    billingApi
      .invoice(id)
      .then((inv) => {
        setInvoice(inv);
        setIrnForm({
          irn: inv.irn ?? "",
          ackNo: inv.ackNo ?? "",
          ackDate: inv.ackDate ?? "",
          signedQr: inv.signedQr ?? "",
        });
      })
      .catch((e) => setError(errorText(e)));
  }, [id]);

  async function submitIrn(e: FormEvent) {
    e.preventDefault();
    if (!invoice) return;
    setIrnBusy(true);
    setIrnMsg(null);
    setError(null);
    try {
      await billingApi.saveInvoiceIrn(invoice.id, irnForm);
      const fresh = await billingApi.invoice(invoice.id);
      setInvoice(fresh);
      setIrnMsg("IRN details saved");
    } catch (err) {
      setError(errorText(err));
    } finally {
      setIrnBusy(false);
    }
  }

  useEffect(() => {
    billingApi.printStatus().then((s) => setServerPrintReady(s.configured)).catch(() => setServerPrintReady(false));
  }, []);

  if (error) {
    return (
      <>
        <PageHeader title="Invoice" />
        <ErrorNote message={error} />
        <Link to="/invoices" className="text-sm text-brand">
          ← Back to invoices
        </Link>
      </>
    );
  }

  if (!invoice) {
    return <PageHeader title="Invoice" subtitle="Loading…" />;
  }

  const placeLabel = invoice.interState
    ? `Other state${invoice.customerStateCode ? ` — ${gstStateName(invoice.customerStateCode)}` : ""}`
    : `Within state${invoice.customerStateCode ? ` — ${gstStateName(invoice.customerStateCode)}` : ""}`;

  const showBrowserPrint = mode === "browser" || !serverPrintReady;
  const isB2b = Boolean(invoice.customerGstin?.trim());

  return (
    <div className="flex flex-col gap-6">
      <ReceiptPrintPanel
        kind="invoice"
        id={invoice.id}
        autoPrint={autoPrint}
      />
      <div className="no-print">
        <PageHeader
          title={invoice.invoiceNo}
          subtitle={new Date(invoice.invoiceDate).toLocaleString("en-IN")}
          actions={
            <div className="flex flex-wrap gap-2">
              {canManage &&
                invoice.lines.some((l) => (l.returnableQtyMilli ?? 0) > 0) && (
                  <Button variant="primary" onClick={() => navigate(`/invoices/${invoice.id}/credit-note`)}>
                    Create credit note
                  </Button>
                )}
              <Button onClick={() => navigate("/")}>New bill</Button>
            </div>
          }
        />
        <div className="mb-4 grid gap-4 rounded-xl border border-line bg-surface p-5 text-sm md:grid-cols-2">
          <div>
            <h2 className="m-0 mb-2 text-xs font-semibold uppercase tracking-wide text-muted">Customer</h2>
            <p className="m-0 font-medium">{invoice.customerName ?? "Walk-in"}</p>
            {invoice.customerPhone && <p className="m-0 text-muted">{invoice.customerPhone}</p>}
            {invoice.customerGstin && <p className="m-0 font-mono text-[13px]">{invoice.customerGstin}</p>}
            {invoice.customerAddress && <p className="m-0 whitespace-pre-wrap text-[13px]">{invoice.customerAddress}</p>}
          </div>
          <div>
            <dl className="m-0 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1">
              <dt className="text-muted">Place of supply</dt>
              <dd className="m-0">{placeLabel}</dd>
              <dt className="text-muted">Payment</dt>
              <dd className="m-0">{invoice.paymentMode}</dd>
              {invoice.createdByName && (
                <>
                  <dt className="text-muted">Billed by</dt>
                  <dd className="m-0">{invoice.createdByName}</dd>
                </>
              )}
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
                <th className="px-2 py-2.5 text-right">Rate</th>
                <th className="px-2 py-2.5 text-right">Disc.</th>
                <th className="px-2 py-2.5 text-right">Taxable</th>
                <th className="px-2 py-2.5 text-right">GST</th>
                <th className="px-4 py-2.5 text-right">Total</th>
              </tr>
            </thead>
            <tbody>
              {invoice.lines.map((l) => (
                <tr key={l.lineNo} className="border-t border-line-soft">
                  <td className="px-4 py-2.5 text-muted">{l.lineNo}</td>
                  <td className="px-2 py-2.5">{l.name}</td>
                  <td className="px-2 py-2.5 text-right font-mono">
                    {formatQty(l.qtyMilli, l.unit)} {l.unit}
                  </td>
                  <td className="px-2 py-2.5 text-right font-mono">{formatPaise(l.ratePaise)}</td>
                  <td className="px-2 py-2.5 text-right font-mono">
                    {l.discountPaise ? formatPaise(l.discountPaise) : "—"}
                  </td>
                  <td className="px-2 py-2.5 text-right font-mono">{formatPaise(l.taxablePaise)}</td>
                  <td className="px-2 py-2.5 text-right">{formatGstRate(l.gstRateBp)}</td>
                  <td className="px-4 py-2.5 text-right font-mono font-medium">{formatPaise(l.totalPaise)}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
        <dl className="m-0 mt-4 grid max-w-sm grid-cols-[1fr_auto] gap-x-3 gap-y-2 text-sm">
          <dt className="text-muted">Gross</dt>
          <dd className="m-0 text-right font-mono">{formatPaise(invoice.grossPaise)}</dd>
          {invoice.discountPaise > 0 && (
            <>
              <dt className="text-muted">Discount</dt>
              <dd className="m-0 text-right font-mono">{formatPaise(invoice.discountPaise)}</dd>
            </>
          )}
          <dt className="text-muted">Taxable</dt>
          <dd className="m-0 text-right font-mono">{formatPaise(invoice.taxablePaise)}</dd>
          <dt className="text-muted">{invoice.interState ? "IGST" : "CGST + SGST"}</dt>
          <dd className="m-0 text-right font-mono">
            {formatPaise(invoice.interState ? invoice.igstPaise : invoice.cgstPaise + invoice.sgstPaise)}
          </dd>
          <dt className="text-muted">Round off</dt>
          <dd className="m-0 text-right font-mono">{formatPaise(invoice.roundOffPaise)}</dd>
          <dt className="font-semibold">Total</dt>
          <dd className="m-0 text-right font-mono text-lg font-semibold">{formatPaise(invoice.totalPaise)}</dd>
        </dl>
        {canManage && isB2b && (
          <form
            onSubmit={(e) => void submitIrn(e)}
            className="mt-4 rounded-xl border border-line bg-surface p-5 text-sm"
          >
            <h2 className="m-0 mb-3 text-xs font-semibold uppercase tracking-wide text-muted">
              {invoice.irn ? "E-invoice (IRN)" : "Add IRN details"}
            </h2>
            <p className="mt-0 mb-3 text-[13px] text-muted">
              After bulk upload on the e-invoice portal, paste the IRN, acknowledgement and signed QR string here.
            </p>
            {irnMsg && <p className="text-brand">{irnMsg}</p>}
            <div className="grid gap-3 md:grid-cols-2">
              <label className="flex flex-col gap-1 md:col-span-2">
                <span className="text-muted">IRN (64 hex)</span>
                <input
                  className="rounded-lg border border-line px-3 py-2 font-mono text-[12px]"
                  value={irnForm.irn}
                  onChange={(e) => setIrnForm({ ...irnForm, irn: e.target.value.trim() })}
                  required
                  maxLength={64}
                />
              </label>
              <label className="flex flex-col gap-1">
                <span className="text-muted">Ack No</span>
                <input
                  className="rounded-lg border border-line px-3 py-2"
                  value={irnForm.ackNo}
                  onChange={(e) => setIrnForm({ ...irnForm, ackNo: e.target.value })}
                  required
                />
              </label>
              <label className="flex flex-col gap-1">
                <span className="text-muted">Ack Date</span>
                <input
                  className="rounded-lg border border-line px-3 py-2"
                  value={irnForm.ackDate}
                  onChange={(e) => setIrnForm({ ...irnForm, ackDate: e.target.value })}
                  placeholder="dd/MM/yyyy or as on portal"
                  required
                />
              </label>
              <label className="flex flex-col gap-1 md:col-span-2">
                <span className="text-muted">Signed QR (JWT from portal)</span>
                <textarea
                  className="min-h-20 rounded-lg border border-line px-3 py-2 font-mono text-[11px]"
                  value={irnForm.signedQr}
                  onChange={(e) => setIrnForm({ ...irnForm, signedQr: e.target.value })}
                  required
                />
              </label>
            </div>
            <Button type="submit" variant="primary" className="mt-3" disabled={irnBusy}>
              {irnBusy ? "Saving…" : "Save IRN details"}
            </Button>
          </form>
        )}
        {(invoice.creditNotes?.length ?? 0) > 0 && (
          <div className="mt-4 rounded-xl border border-line bg-surface p-4 text-sm">
            <h2 className="m-0 mb-2 text-xs font-semibold uppercase tracking-wide text-muted">Credit notes</h2>
            <ul className="m-0 list-none space-y-1 p-0">
              {invoice.creditNotes!.map((cn) => (
                <li key={cn.id}>
                  <Link to={`/credit-notes/${cn.id}`} className="text-brand">
                    {cn.cnNo}
                  </Link>
                  <span className="text-muted">
                    {" "}
                    · {new Date(cn.cnDate).toLocaleDateString("en-IN")} · {formatPaise(cn.totalPaise)}
                  </span>
                </li>
              ))}
            </ul>
          </div>
        )}
        <p className="mt-4">
          <Link to="/invoices" className="text-sm text-brand">
            ← All invoices
          </Link>
        </p>
      </div>

      {showBrowserPrint && (
        <PrintDocument
          invoice={invoice}
          shop={shop}
          layout={layout}
          onLayoutChange={setLayout}
          onNewBill={() => navigate("/")}
        />
      )}
    </div>
  );
}
