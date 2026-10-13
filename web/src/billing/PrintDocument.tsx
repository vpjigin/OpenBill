import { useEffect } from "react";
import { QRCodeSVG } from "qrcode.react";
import { gstStateName } from "../shared/gstStates";
import { formatGstRate, formatPaise, formatQty } from "../shared/money";
import { amountInWords } from "../shared/words";
import { Button } from "../shared/ui";
import type { CreditNote, Invoice, InvoiceLine, ShopInfo } from "./billingApi";
import type { PrintLayout } from "./usePrintLayout";

export function creditNoteToPrintInvoice(cn: CreditNote): Invoice {
  const lines: InvoiceLine[] = cn.lines.map((l) => ({
    lineNo: l.lineNo,
    name: l.name,
    hsn: l.hsn,
    qtyMilli: l.qtyMilli,
    unit: l.unit,
    taxInclusive: false,
    ratePaise: l.ratePaise,
    gstRateBp: l.gstRateBp,
    grossPaise: l.taxablePaise,
    discountPaise: 0,
    taxablePaise: l.taxablePaise,
    cgstPaise: l.cgstPaise,
    sgstPaise: l.sgstPaise,
    igstPaise: l.igstPaise,
    totalPaise: l.totalPaise,
  }));
  return {
    id: cn.id,
    invoiceNo: cn.cnNo,
    invoiceDate: cn.cnDate,
    customerId: cn.customerId,
    customerName: cn.customerName,
    customerPhone: null,
    customerGstin: cn.customerGstin,
    customerStateCode: cn.customerStateCode,
    customerAddress: null,
    interState: cn.interState,
    paymentMode: cn.refundMode,
    grossPaise: cn.taxablePaise,
    discountPaise: 0,
    billDiscountPaise: 0,
    taxablePaise: cn.taxablePaise,
    cgstPaise: cn.cgstPaise,
    sgstPaise: cn.sgstPaise,
    igstPaise: cn.igstPaise,
    roundOffPaise: cn.roundOffPaise,
    totalPaise: cn.totalPaise,
    irn: null,
    ackNo: null,
    ackDate: null,
    signedQr: null,
    createdByName: null,
    lines,
  };
}

interface TaxBucket {
  gstRateBp: number;
  taxable: number;
  cgst: number;
  sgst: number;
  igst: number;
}

function taxBuckets(lines: InvoiceLine[]): TaxBucket[] {
  const map = new Map<number, TaxBucket>();
  for (const l of lines) {
    let b = map.get(l.gstRateBp);
    if (!b) {
      b = { gstRateBp: l.gstRateBp, taxable: 0, cgst: 0, sgst: 0, igst: 0 };
      map.set(l.gstRateBp, b);
    }
    b.taxable += l.taxablePaise;
    b.cgst += l.cgstPaise;
    b.sgst += l.sgstPaise;
    b.igst += l.igstPaise;
  }
  return [...map.values()].sort((a, b) => a.gstRateBp - b.gstRateBp);
}

export function PrintDocument({
  invoice,
  shop,
  layout,
  onLayoutChange,
  onNewBill,
  documentTitle = "TAX INVOICE",
  againstLabel,
  reason,
  paymentLabel,
}: {
  invoice: Invoice;
  shop: ShopInfo | null;
  layout: PrintLayout;
  onLayoutChange: (l: PrintLayout) => void;
  onNewBill?: () => void;
  documentTitle?: string;
  againstLabel?: string;
  reason?: string;
  paymentLabel?: string;
}) {
  useEffect(() => {
    const id = "billing-print-page";
    let el = document.getElementById(id) as HTMLStyleElement | null;
    if (!el) {
      el = document.createElement("style");
      el.id = id;
      document.head.appendChild(el);
    }
    el.textContent =
      layout === "80mm"
        ? "@media print { @page { size: 80mm auto; margin: 3mm; } body { margin: 0; } }"
        : "@media print { @page { size: A4; margin: 12mm; } }";
  }, [layout]);

  const date = new Date(invoice.invoiceDate);
  const buckets = taxBuckets(invoice.lines);
  const placeLabel = invoice.interState
    ? `Other state${invoice.customerStateCode ? ` — ${gstStateName(invoice.customerStateCode)}` : ""}`
    : `Within state${invoice.customerStateCode ? ` — ${gstStateName(invoice.customerStateCode)}` : ""}`;

  return (
    <div className="flex flex-col items-center gap-4">
      <div className="no-print flex w-full max-w-3xl flex-wrap items-center justify-between gap-3">
        <div className="flex flex-wrap items-center gap-2">
          <span className="text-sm font-medium">{invoice.invoiceNo}</span>
          <LayoutToggle layout={layout} onChange={onLayoutChange} />
        </div>
        <div className="flex gap-2">
          <Button onClick={() => window.print()}>Print</Button>
          {onNewBill && (
            <Button variant="primary" onClick={onNewBill}>
              New bill
            </Button>
          )}
        </div>
      </div>

      {layout === "80mm" ? (
        <article className="print-doc w-full max-w-[80mm] bg-surface p-3 font-mono text-[11px] leading-snug text-black print:max-w-none print:p-0">
          <div className="text-center">
            {shop?.logoObjectUrl && shop.receiptShowLogo && (
              <img src={shop.logoObjectUrl} alt="" className="mx-auto mb-2 max-h-12 max-w-[70%] object-contain" />
            )}
            <div className="text-[13px] font-semibold">{shop?.shopName ?? "Shop"}</div>
            {shop?.address && <div className="whitespace-pre-wrap">{shop.address}</div>}
            {shop?.phone && <div>Ph: {shop.phone}</div>}
            {shop?.gstin && <div>GSTIN: {shop.gstin}</div>}
            <div className="mt-1 text-[12px] font-semibold">{documentTitle}</div>
            <div>{invoice.invoiceNo}</div>
            {againstLabel && <div>{againstLabel}</div>}
            {reason && <div>Reason: {reason}</div>}
            <div>{date.toLocaleString("en-IN")}</div>
          </div>
          <hr className="my-2 border-dashed border-black" />
          {(invoice.customerName || invoice.customerPhone) && (
            <div className="mb-1">
              {invoice.customerName && <div>{invoice.customerName}</div>}
              {invoice.customerPhone && <div>{invoice.customerPhone}</div>}
              {invoice.customerGstin && <div>GSTIN: {invoice.customerGstin}</div>}
            </div>
          )}
          {invoice.lines.map((l) => (
            <div key={l.lineNo} className="mb-1.5">
              <div>{l.name}</div>
              <div className="flex justify-between gap-1">
                <span>
                  {formatQty(l.qtyMilli, l.unit)} {l.unit} × {formatPaise(l.ratePaise)}
                </span>
                <span>{formatPaise(l.grossPaise)}</span>
              </div>
              {l.discountPaise > 0 && (
                <div className="flex justify-between text-[10px]">
                  <span>Disc.</span>
                  <span>−{formatPaise(l.discountPaise)}</span>
                </div>
              )}
              <div className="flex justify-between font-medium">
                <span>Amount</span>
                <span>{formatPaise(l.totalPaise)}</span>
              </div>
            </div>
          ))}
          <hr className="my-2 border-dashed border-black" />
          <PrintRow k="Gross" v={invoice.grossPaise} />
          {invoice.discountPaise > 0 && <PrintRow k="Discount" v={-invoice.discountPaise} />}
          <PrintRow k="Taxable" v={invoice.taxablePaise} />
          {invoice.interState ? (
            <PrintRow k="IGST" v={invoice.igstPaise} />
          ) : (
            <>
              <PrintRow k="CGST" v={invoice.cgstPaise} />
              <PrintRow k="SGST" v={invoice.sgstPaise} />
            </>
          )}
          <PrintRow k="Round off" v={invoice.roundOffPaise} />
          <div className="mt-1 flex justify-between text-[12px] font-bold">
            <span>TOTAL</span>
            <span>{formatPaise(invoice.totalPaise)}</span>
          </div>
          <div className="mt-1">{paymentLabel ?? `Paid by ${invoice.paymentMode}`}</div>
          <div className="mt-2 text-center">Thank you!</div>
        </article>
      ) : (
        <article className="print-doc w-full max-w-[210mm] bg-surface p-6 text-[13px] text-black print:max-w-none print:p-0">
          <h1 className="m-0 mb-4 text-center text-xl font-bold tracking-wide">{documentTitle}</h1>
          {againstLabel && <p className="-mt-2 mb-4 text-center text-[12px]">{againstLabel}</p>}
          {reason && <p className="-mt-2 mb-4 text-center text-[12px]">Reason: {reason}</p>}
          <div className="mb-4 grid grid-cols-2 gap-6 border-b border-black pb-4">
            <div>
              {shop?.logoObjectUrl && (
                <img
                  src={shop.logoObjectUrl}
                  alt=""
                  className="mb-2 max-h-16 max-w-[180px] object-contain object-left"
                />
              )}
              <div className="font-semibold">{shop?.shopName}</div>
              {shop?.address && <div className="whitespace-pre-wrap text-[12px]">{shop.address}</div>}
              {shop?.phone && <div>Phone: {shop.phone}</div>}
              {shop?.gstin && <div className="font-mono text-[12px]">GSTIN: {shop.gstin}</div>}
              {shop?.stateCode && (
                <div className="text-[12px]">
                  State: {shop.stateCode} — {shop.stateName ?? gstStateName(shop.stateCode)}
                </div>
              )}
            </div>
            <div className="text-right text-[12px]">
              <div>
                <span className="font-semibold">{documentTitle === "CREDIT NOTE" ? "Credit note:" : "Invoice:"}</span>{" "}
                {invoice.invoiceNo}
              </div>
              <div>
                <span className="font-semibold">Date:</span> {date.toLocaleString("en-IN")}
              </div>
              <div>
                <span className="font-semibold">{paymentLabel ? "Refund:" : "Payment:"}</span>{" "}
                {paymentLabel ?? invoice.paymentMode}
              </div>
              {invoice.createdByName && (
                <div>
                  <span className="font-semibold">Billed by:</span> {invoice.createdByName}
                </div>
              )}
            </div>
          </div>
          <div className="mb-4 grid grid-cols-2 gap-6 border-b border-black pb-4 text-[12px]">
            <div>
              <div className="mb-1 font-semibold uppercase tracking-wide">Bill to</div>
              <div>{invoice.customerName ?? "Walk-in"}</div>
              {invoice.customerPhone && <div>{invoice.customerPhone}</div>}
              {invoice.customerGstin && <div className="font-mono">GSTIN: {invoice.customerGstin}</div>}
              {invoice.customerAddress && <div className="whitespace-pre-wrap">{invoice.customerAddress}</div>}
            </div>
            <div>
              <div className="mb-1 font-semibold uppercase tracking-wide">Place of supply</div>
              <div>{placeLabel}</div>
            </div>
          </div>
          <table className="mb-4 w-full border-collapse text-[11px]">
            <thead>
              <tr className="border border-black bg-neutral-100">
                <th className="border border-black px-1 py-1">#</th>
                <th className="border border-black px-1 py-1 text-left">Item</th>
                <th className="border border-black px-1 py-1">HSN</th>
                <th className="border border-black px-1 py-1">Qty</th>
                <th className="border border-black px-1 py-1 text-right">Rate</th>
                <th className="border border-black px-1 py-1 text-right">Disc.</th>
                <th className="border border-black px-1 py-1 text-right">Taxable</th>
                <th className="border border-black px-1 py-1">GST%</th>
                <th className="border border-black px-1 py-1 text-right">Tax</th>
                <th className="border border-black px-1 py-1 text-right">Amount</th>
              </tr>
            </thead>
            <tbody>
              {invoice.lines.map((l) => {
                const tax = l.cgstPaise + l.sgstPaise + l.igstPaise;
                return (
                  <tr key={l.lineNo}>
                    <td className="border border-black px-1 py-1 text-center">{l.lineNo}</td>
                    <td className="border border-black px-1 py-1">{l.name}</td>
                    <td className="border border-black px-1 py-1 font-mono">{l.hsn ?? "—"}</td>
                    <td className="border border-black px-1 py-1 whitespace-nowrap">
                      {formatQty(l.qtyMilli, l.unit)} {l.unit}
                    </td>
                    <td className="border border-black px-1 py-1 text-right font-mono">{formatPaise(l.ratePaise)}</td>
                    <td className="border border-black px-1 py-1 text-right font-mono">
                      {l.discountPaise ? formatPaise(l.discountPaise) : "—"}
                    </td>
                    <td className="border border-black px-1 py-1 text-right font-mono">{formatPaise(l.taxablePaise)}</td>
                    <td className="border border-black px-1 py-1 text-center">{formatGstRate(l.gstRateBp)}</td>
                    <td className="border border-black px-1 py-1 text-right font-mono">{formatPaise(tax)}</td>
                    <td className="border border-black px-1 py-1 text-right font-mono">{formatPaise(l.totalPaise)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
          <div className="mb-4 grid grid-cols-2 gap-6">
            <div>
              <div className="mb-2 font-semibold">Tax summary</div>
              <table className="w-full border-collapse text-[11px]">
                <thead>
                  <tr className="border border-black">
                    <th className="border border-black px-2 py-1">Rate</th>
                    <th className="border border-black px-2 py-1 text-right">Taxable</th>
                    {invoice.interState ? (
                      <th className="border border-black px-2 py-1 text-right">IGST</th>
                    ) : (
                      <>
                        <th className="border border-black px-2 py-1 text-right">CGST</th>
                        <th className="border border-black px-2 py-1 text-right">SGST</th>
                      </>
                    )}
                  </tr>
                </thead>
                <tbody>
                  {buckets.map((b) => (
                    <tr key={b.gstRateBp}>
                      <td className="border border-black px-2 py-1">{formatGstRate(b.gstRateBp)}</td>
                      <td className="border border-black px-2 py-1 text-right font-mono">{formatPaise(b.taxable)}</td>
                      {invoice.interState ? (
                        <td className="border border-black px-2 py-1 text-right font-mono">{formatPaise(b.igst)}</td>
                      ) : (
                        <>
                          <td className="border border-black px-2 py-1 text-right font-mono">{formatPaise(b.cgst)}</td>
                          <td className="border border-black px-2 py-1 text-right font-mono">{formatPaise(b.sgst)}</td>
                        </>
                      )}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
            <div className="text-[12px]">
              <PrintRow k="Gross" v={invoice.grossPaise} />
              {invoice.discountPaise > 0 && <PrintRow k="Discount" v={-invoice.discountPaise} />}
              <PrintRow k="Taxable" v={invoice.taxablePaise} />
              {invoice.interState ? (
                <PrintRow k="IGST" v={invoice.igstPaise} />
              ) : (
                <>
                  <PrintRow k="CGST" v={invoice.cgstPaise} />
                  <PrintRow k="SGST" v={invoice.sgstPaise} />
                </>
              )}
              <PrintRow k="Round off" v={invoice.roundOffPaise} />
              <div className="mt-2 flex justify-between border-t border-black pt-2 text-base font-bold">
                <span>Total</span>
                <span className="font-mono">{formatPaise(invoice.totalPaise)}</span>
              </div>
              <p className="mt-3 text-[11px] leading-snug italic">{amountInWords(invoice.totalPaise)}</p>
            </div>
          </div>
          {invoice.irn && (
            <div className="mb-4 grid grid-cols-[1fr_auto] gap-4 border border-black p-3 text-[11px]">
              <div className="space-y-1">
                <div>
                  <span className="font-semibold">IRN:</span>{" "}
                  <span className="break-all font-mono text-[10px]">{invoice.irn}</span>
                </div>
                {invoice.ackNo && (
                  <div>
                    <span className="font-semibold">Ack No:</span> {invoice.ackNo}
                  </div>
                )}
                {invoice.ackDate && (
                  <div>
                    <span className="font-semibold">Ack Date:</span> {invoice.ackDate}
                  </div>
                )}
              </div>
              {invoice.signedQr && (
                <QRCodeSVG value={invoice.signedQr} size={96} level="M" includeMargin />
              )}
            </div>
          )}
          <div className="mt-12 text-right text-[12px]">
            For {shop?.shopName ?? "________________"}
            <div className="mt-10 border-t border-black pt-1">Authorised signatory</div>
          </div>
        </article>
      )}
    </div>
  );
}

function LayoutToggle({ layout, onChange }: { layout: PrintLayout; onChange: (l: PrintLayout) => void }) {
  return (
    <div className="flex overflow-hidden rounded-md border border-line text-[12px] font-semibold">
      <button
        type="button"
        className={`px-3 py-1.5 ${layout === "80mm" ? "bg-brand text-white" : "bg-surface"}`}
        onClick={() => onChange("80mm")}
      >
        80 mm
      </button>
      <button
        type="button"
        className={`px-3 py-1.5 ${layout === "a4" ? "bg-brand text-white" : "bg-surface"}`}
        onClick={() => onChange("a4")}
      >
        A4
      </button>
    </div>
  );
}

function PrintRow({ k, v }: { k: string; v: number }) {
  return (
    <div className="flex justify-between gap-4">
      <span>{k}</span>
      <span className="font-mono">{formatPaise(v)}</span>
    </div>
  );
}
