import { useCallback, useEffect, useMemo, useState } from "react";
import { formatGstRate, formatPaise, formatQty } from "../../shared/money";
import { Button, ErrorNote, PageHeader, errorText } from "../../shared/ui";
import { ReceiptPrintPanel } from "../ReceiptPrintPanel";
import { usePrintLayout } from "../usePrintLayout";
import { billingApi } from "../billingApi";
import {
  csvEscape,
  reportsApi,
  type DailySalesRow,
  type DayEndReport,
  type HsnRow,
  type PurchaseTotals,
  type ReportTotals,
} from "../reportsApi";

type Tab = "day-end" | "sales" | "hsn" | "gstr1" | "einvoice";

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

function currentMonthIso() {
  const d = new Date();
  return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, "0")}`;
}

function sumTotals(rows: DailySalesRow[]): ReportTotals {
  return rows.reduce(
    (acc, r) => ({
      billsCount: acc.billsCount + r.totals.billsCount,
      grossPaise: acc.grossPaise + r.totals.grossPaise,
      discountPaise: acc.discountPaise + r.totals.discountPaise,
      taxablePaise: acc.taxablePaise + r.totals.taxablePaise,
      cgstPaise: acc.cgstPaise + r.totals.cgstPaise,
      sgstPaise: acc.sgstPaise + r.totals.sgstPaise,
      igstPaise: acc.igstPaise + r.totals.igstPaise,
      roundOffPaise: acc.roundOffPaise + r.totals.roundOffPaise,
      totalPaise: acc.totalPaise + r.totals.totalPaise,
    }),
    {
      billsCount: 0,
      grossPaise: 0,
      discountPaise: 0,
      taxablePaise: 0,
      cgstPaise: 0,
      sgstPaise: 0,
      igstPaise: 0,
      roundOffPaise: 0,
      totalPaise: 0,
    },
  );
}

export function ReportsPage() {
  const [tab, setTab] = useState<Tab>("day-end");
  const [error, setError] = useState<string | null>(null);
  const [dayDate, setDayDate] = useState(todayIso);
  const [rangeFrom, setRangeFrom] = useState(() => {
    const d = new Date();
    d.setDate(d.getDate() - 7);
    return d.toISOString().slice(0, 10);
  });
  const [rangeTo, setRangeTo] = useState(todayIso);
  const [dayEnd, setDayEnd] = useState<DayEndReport | null>(null);
  const [salesRows, setSalesRows] = useState<DailySalesRow[]>([]);
  const [hsnRows, setHsnRows] = useState<HsnRow[]>([]);
  const [gstrMonth, setGstrMonth] = useState(currentMonthIso);
  const [gstrBusy, setGstrBusy] = useState(false);
  const [einvoiceProblems, setEinvoiceProblems] = useState<string[] | null>(null);
  const [einvoiceBusy, setEinvoiceBusy] = useState(false);
  const { layout, setLayout } = usePrintLayout();

  const loadDayEnd = useCallback(() => {
    setError(null);
    reportsApi.dayEnd(dayDate).then(setDayEnd).catch((e) => setError(errorText(e)));
  }, [dayDate]);

  const loadSales = useCallback(() => {
    setError(null);
    reportsApi
      .sales(rangeFrom, rangeTo)
      .then(setSalesRows)
      .catch((e) => setError(errorText(e)));
  }, [rangeFrom, rangeTo]);

  const loadHsn = useCallback(() => {
    setError(null);
    reportsApi
      .hsn(rangeFrom, rangeTo)
      .then(setHsnRows)
      .catch((e) => setError(errorText(e)));
  }, [rangeFrom, rangeTo]);

  useEffect(() => {
    if (tab === "day-end") loadDayEnd();
    if (tab === "sales") loadSales();
    if (tab === "hsn") loadHsn();
  }, [tab, loadDayEnd, loadSales, loadHsn]);

  const salesTotals = useMemo(() => sumTotals(salesRows), [salesRows]);

  function downloadSalesCsv() {
    const header = ["Date", "Bills", "Gross", "Discount", "Taxable", "CGST", "SGST", "IGST", "Round off", "Total"];
    const lines = [header.join(",")];
    for (const r of salesRows) {
      const t = r.totals;
      lines.push(
        [
          r.date,
          t.billsCount,
          t.grossPaise,
          t.discountPaise,
          t.taxablePaise,
          t.cgstPaise,
          t.sgstPaise,
          t.igstPaise,
          t.roundOffPaise,
          t.totalPaise,
        ]
          .map(csvEscape)
          .join(","),
      );
    }
    const t = salesTotals;
    lines.push(
      ["Total", t.billsCount, t.grossPaise, t.discountPaise, t.taxablePaise, t.cgstPaise, t.sgstPaise, t.igstPaise, t.roundOffPaise, t.totalPaise]
        .map(csvEscape)
        .join(","),
    );
    downloadCsv(`sales-${rangeFrom}-to-${rangeTo}.csv`, lines.join("\n"));
  }

  function downloadHsnCsv() {
    const header = ["HSN", "GST %", "Unit", "Qty", "Taxable (paise)", "CGST", "SGST", "IGST", "Total (paise)"];
    const lines = [
      header.join(","),
      ...hsnRows.map((r) =>
        [r.hsn || "", r.gstRateBp, r.unit, formatQty(r.qtyMilli, r.unit), r.taxablePaise, r.cgstPaise, r.sgstPaise, r.igstPaise, r.totalPaise]
          .map(csvEscape)
          .join(","),
      ),
    ];
    downloadCsv(`hsn-${rangeFrom}-to-${rangeTo}.csv`, lines.join("\n"));
  }

  async function checkEinvoice() {
    setError(null);
    setEinvoiceBusy(true);
    setEinvoiceProblems(null);
    try {
      const res = await billingApi.einvoiceExport(rangeFrom, rangeTo);
      if (res.problems.length > 0) {
        setEinvoiceProblems(res.problems);
      } else {
        const blob = new Blob([JSON.stringify(res.documents ?? [], null, 2)], {
          type: "application/json;charset=utf-8",
        });
        const url = URL.createObjectURL(blob);
        const a = document.createElement("a");
        a.href = url;
        a.download = `einvoice-${rangeFrom}-to-${rangeTo}.json`;
        a.click();
        URL.revokeObjectURL(url);
        setEinvoiceProblems([]);
      }
    } catch (e) {
      setError(errorText(e));
    } finally {
      setEinvoiceBusy(false);
    }
  }

  async function downloadGstr1() {
    setError(null);
    setGstrBusy(true);
    try {
      const { blob, filename } = await reportsApi.downloadGstr1(gstrMonth);
      const url = URL.createObjectURL(blob);
      const a = document.createElement("a");
      a.href = url;
      a.download = filename;
      a.click();
      URL.revokeObjectURL(url);
    } catch (e) {
      setError(errorText(e));
    } finally {
      setGstrBusy(false);
    }
  }

  return (
    <>
      <div className="no-print">
        <PageHeader title="Reports" subtitle="Day-end, sales and HSN (GST returns)" />
        <ErrorNote message={error} />
        <div className="mb-4 flex flex-wrap gap-2">
          {(
            [
              ["day-end", "Day-end"],
              ["sales", "Sales"],
              ["hsn", "HSN summary"],
              ["gstr1", "GSTR-1"],
              ["einvoice", "E-invoice"],
            ] as const
          ).map(([id, label]) => (
            <button
              key={id}
              type="button"
              onClick={() => setTab(id)}
              className={`min-h-10 rounded-lg px-4 text-sm font-semibold ${
                tab === id ? "bg-brand text-white" : "border border-line bg-surface"
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        {tab === "day-end" && (
          <div className="mb-4 flex flex-wrap items-end gap-3">
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-muted">Date</span>
              <input type="date" className="rounded-lg border border-line px-2 py-1.5" value={dayDate} onChange={(e) => setDayDate(e.target.value)} />
            </label>
            <Button onClick={loadDayEnd}>Refresh</Button>
            <Button onClick={() => window.print()}>Print</Button>
            <LayoutToggle layout={layout} onChange={setLayout} />
          </div>
        )}

        {tab === "gstr1" && (
          <div className="max-w-lg space-y-3">
            <div className="flex flex-wrap items-end gap-3">
              <label className="flex flex-col gap-1 text-sm">
                <span className="text-muted">Return month</span>
                <input
                  type="month"
                  className="rounded-lg border border-line px-2 py-1.5"
                  value={gstrMonth}
                  onChange={(e) => setGstrMonth(e.target.value)}
                />
              </label>
              <Button onClick={() => void downloadGstr1()} disabled={gstrBusy}>
                {gstrBusy ? "Preparing…" : "Download GSTR-1 (zip)"}
              </Button>
            </div>
            <p className="text-sm text-muted">
              Check the columns against the current GST offline tool template before filing.
            </p>
          </div>
        )}

        {tab === "einvoice" && (
          <div className="max-w-2xl space-y-3">
            <div className="flex flex-wrap items-end gap-3">
              <label className="flex flex-col gap-1 text-sm">
                <span className="text-muted">From</span>
                <input
                  type="date"
                  className="rounded-lg border border-line px-2 py-1.5"
                  value={rangeFrom}
                  onChange={(e) => setRangeFrom(e.target.value)}
                />
              </label>
              <label className="flex flex-col gap-1 text-sm">
                <span className="text-muted">To</span>
                <input
                  type="date"
                  className="rounded-lg border border-line px-2 py-1.5"
                  value={rangeTo}
                  onChange={(e) => setRangeTo(e.target.value)}
                />
              </label>
              <Button variant="primary" onClick={() => void checkEinvoice()} disabled={einvoiceBusy}>
                {einvoiceBusy ? "Checking…" : "Validate / download JSON"}
              </Button>
            </div>
            <p className="text-sm text-muted">
              Exports B2B invoices without an IRN (and credit notes) for bulk upload on the e-invoice portal. Fix any
              problems below before downloading. Verify fields against the current NIC schema before filing.
            </p>
            {einvoiceProblems != null && einvoiceProblems.length > 0 && (
              <ul className="m-0 list-disc space-y-1 rounded-lg border border-amber-200 bg-amber-50 px-5 py-3 text-sm text-amber-950">
                {einvoiceProblems.map((p) => (
                  <li key={p}>{p}</li>
                ))}
              </ul>
            )}
            {einvoiceProblems != null && einvoiceProblems.length === 0 && (
              <p className="text-sm text-brand">Download started — no validation issues in this range.</p>
            )}
          </div>
        )}

        {(tab === "sales" || tab === "hsn") && (
          <div className="mb-4 flex flex-wrap items-end gap-3">
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-muted">From</span>
              <input type="date" className="rounded-lg border border-line px-2 py-1.5" value={rangeFrom} onChange={(e) => setRangeFrom(e.target.value)} />
            </label>
            <label className="flex flex-col gap-1 text-sm">
              <span className="text-muted">To</span>
              <input type="date" className="rounded-lg border border-line px-2 py-1.5" value={rangeTo} onChange={(e) => setRangeTo(e.target.value)} />
            </label>
            <Button onClick={tab === "sales" ? loadSales : loadHsn}>Refresh</Button>
            {tab === "sales" && <Button onClick={downloadSalesCsv}>Download CSV</Button>}
            {tab === "hsn" && <Button onClick={downloadHsnCsv}>Download CSV</Button>}
          </div>
        )}

        {tab === "day-end" && dayEnd && (
          <>
            <ReceiptPrintPanel kind="day-end" dayEndDate={dayDate} />
            <DayEndScreen report={dayEnd} layout={layout} />
          </>
        )}

        {tab === "sales" && (
          <SalesTable rows={salesRows} totals={salesTotals} />
        )}

        {tab === "hsn" && <HsnTable rows={hsnRows} />}
      </div>

      {tab === "day-end" && dayEnd && (
        <div className="print-only hidden print:block">
          <DayEndPrint report={dayEnd} layout={layout} />
        </div>
      )}
    </>
  );
}

function DayEndScreen({ report, layout }: { report: DayEndReport; layout: "80mm" | "a4" }) {
  return (
    <div className={layout === "80mm" ? "max-w-[80mm]" : "max-w-3xl"}>
      <TotalsCards totals={report.totals} />
      <PurchaseBlock purchases={report.purchases} />
      <ModeTable report={report} />
      {(report.firstInvoiceNo || report.lastInvoiceNo) && (
        <p className="mt-3 text-sm text-muted">
          Invoices: {report.firstInvoiceNo ?? "—"} … {report.lastInvoiceNo ?? "—"}
        </p>
      )}
    </div>
  );
}

function DayEndPrint({ report, layout }: { report: DayEndReport; layout: "80mm" | "a4" }) {
  const date = new Date(report.date).toLocaleDateString("en-IN", { weekday: "long", day: "numeric", month: "short", year: "numeric" });
  return (
    <article className={`print-doc text-black ${layout === "80mm" ? "font-mono text-[11px]" : "text-sm"}`}>
      <h1 className="text-center text-base font-bold">Day-end report</h1>
      <p className="text-center">{date}</p>
      <TotalsPrint totals={report.totals} />
      <PurchasePrint purchases={report.purchases} />
      <ModeTable report={report} compact />
    </article>
  );
}

function TotalsCards({ totals }: { totals: ReportTotals }) {
  return (
    <div className="mb-4 grid grid-cols-[repeat(auto-fit,minmax(140px,1fr))] gap-3">
      <StatCard label="Bills" value={String(totals.billsCount)} />
      <StatCard label="Total sales" value={formatPaise(totals.totalPaise)} />
      <StatCard label="Discounts" value={formatPaise(totals.discountPaise)} />
      <StatCard label="GST" value={formatPaise(totals.cgstPaise + totals.sgstPaise + totals.igstPaise)} />
    </div>
  );
}

function StatCard({ label, value }: { label: string; value: string }) {
  return (
    <div className="rounded-xl border border-line bg-surface px-4 py-3">
      <div className="text-xs text-muted">{label}</div>
      <div className="font-mono text-lg font-medium">{value}</div>
    </div>
  );
}

function PurchaseBlock({ purchases }: { purchases: PurchaseTotals }) {
  if (purchases.count === 0) return null;
  return (
    <div className="mb-4 rounded-xl border border-line bg-surface px-4 py-3">
      <h3 className="m-0 mb-2 text-sm font-semibold">Purchases (input GST)</h3>
      <dl className="grid grid-cols-[1fr_auto] gap-x-3 gap-y-1 text-sm">
        <dt>Bills</dt>
        <dd className="m-0 text-right font-mono">{purchases.count}</dd>
        <dt>Taxable</dt>
        <dd className="m-0 text-right font-mono">{formatPaise(purchases.taxablePaise)}</dd>
        <dt>Input GST</dt>
        <dd className="m-0 text-right font-mono">
          {formatPaise(purchases.cgstPaise + purchases.sgstPaise + purchases.igstPaise)}
        </dd>
        <dt>Total</dt>
        <dd className="m-0 text-right font-mono font-semibold">{formatPaise(purchases.totalPaise)}</dd>
      </dl>
    </div>
  );
}

function PurchasePrint({ purchases }: { purchases: PurchaseTotals }) {
  if (purchases.count === 0) return null;
  return (
    <div className="my-2 border-t border-dashed border-neutral-400 pt-2 text-sm">
      <div className="font-semibold">Purchases</div>
      <dl className="grid grid-cols-[1fr_auto] gap-x-2">
        <dt>Count</dt>
        <dd className="m-0 text-right font-mono">{purchases.count}</dd>
        <dt>Total</dt>
        <dd className="m-0 text-right font-mono">{formatPaise(purchases.totalPaise)}</dd>
      </dl>
    </div>
  );
}

function TotalsPrint({ totals }: { totals: ReportTotals }) {
  return (
    <dl className="my-3 grid grid-cols-[1fr_auto] gap-x-3 gap-y-1 text-sm">
      <dt>Bills</dt>
      <dd className="m-0 text-right font-mono">{totals.billsCount}</dd>
      <dt>Gross</dt>
      <dd className="m-0 text-right font-mono">{formatPaise(totals.grossPaise)}</dd>
      <dt>Discount</dt>
      <dd className="m-0 text-right font-mono">{formatPaise(totals.discountPaise)}</dd>
      <dt>Taxable</dt>
      <dd className="m-0 text-right font-mono">{formatPaise(totals.taxablePaise)}</dd>
      <dt>GST</dt>
      <dd className="m-0 text-right font-mono">{formatPaise(totals.cgstPaise + totals.sgstPaise + totals.igstPaise)}</dd>
      <dt>Total</dt>
      <dd className="m-0 text-right font-mono font-semibold">{formatPaise(totals.totalPaise)}</dd>
    </dl>
  );
}

function ModeTable({ report, compact }: { report: DayEndReport; compact?: boolean }) {
  const modes = new Set<string>();
  report.salesByMode.forEach((m) => modes.add(m.mode));
  report.creditNotesByMode.forEach((m) => modes.add(m.mode));
  report.netByMode.forEach((m) => modes.add(m.mode));
  const ordered = [...modes].sort();
  const salesMap = new Map(report.salesByMode.map((m) => [m.mode, m.totalPaise]));
  const creditMap = new Map(report.creditNotesByMode.map((m) => [m.mode, m]));
  const netMap = new Map(report.netByMode.map((m) => [m.mode, m.netPaise]));

  if (ordered.length === 0) {
    return <p className="text-sm text-muted">No payments recorded for this day.</p>;
  }

  return (
    <div className={`overflow-x-auto rounded-xl border border-line bg-surface ${compact ? "text-[11px]" : "text-sm"}`}>
      <table className="w-full min-w-[480px] border-collapse">
        <thead>
          <tr className="text-left text-xs uppercase tracking-wider text-muted">
            <th className="px-4 py-2">Mode</th>
            <th className="px-2 py-2 text-right">Sales</th>
            <th className="px-2 py-2 text-right">Refunds</th>
            <th className="px-4 py-2 text-right">Net</th>
          </tr>
        </thead>
        <tbody>
          {ordered.map((mode) => {
            const cr = creditMap.get(mode);
            return (
              <tr key={mode} className="border-t border-line-soft">
                <td className="px-4 py-2 font-medium">{mode}</td>
                <td className="px-2 py-2 text-right font-mono">{formatPaise(salesMap.get(mode) ?? 0)}</td>
                <td className="px-2 py-2 text-right font-mono">
                  {cr ? `${cr.count} · ${formatPaise(cr.totalPaise)}` : "—"}
                </td>
                <td className="px-4 py-2 text-right font-mono font-semibold">{formatPaise(netMap.get(mode) ?? (salesMap.get(mode) ?? 0))}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
    </div>
  );
}

function SalesTable({ rows, totals }: { rows: DailySalesRow[]; totals: ReportTotals }) {
  return (
    <div className="overflow-x-auto rounded-xl border border-line bg-surface">
      <table className="w-full min-w-[720px] border-collapse text-sm">
        <thead>
          <tr className="text-left text-xs uppercase tracking-wider text-muted">
            <th className="px-4 py-2">Date</th>
            <th className="px-2 py-2 text-right">Bills</th>
            <th className="px-2 py-2 text-right">Gross</th>
            <th className="px-2 py-2 text-right">Discount</th>
            <th className="px-2 py-2 text-right">Taxable</th>
            <th className="px-2 py-2 text-right">GST</th>
            <th className="px-2 py-2 text-right">Purch.</th>
            <th className="px-4 py-2 text-right">Sales total</th>
            <th className="px-4 py-2 text-right">Purchase total</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => (
            <tr key={r.date} className="border-t border-line-soft">
              <td className="px-4 py-2">{r.date}</td>
              <td className="px-2 py-2 text-right font-mono">{r.totals.billsCount}</td>
              <td className="px-2 py-2 text-right font-mono">{formatPaise(r.totals.grossPaise)}</td>
              <td className="px-2 py-2 text-right font-mono">{formatPaise(r.totals.discountPaise)}</td>
              <td className="px-2 py-2 text-right font-mono">{formatPaise(r.totals.taxablePaise)}</td>
              <td className="px-2 py-2 text-right font-mono">
                {formatPaise(r.totals.cgstPaise + r.totals.sgstPaise + r.totals.igstPaise)}
              </td>
              <td className="px-2 py-2 text-right font-mono">{r.purchases.count}</td>
              <td className="px-4 py-2 text-right font-mono font-medium">{formatPaise(r.totals.totalPaise)}</td>
              <td className="px-4 py-2 text-right font-mono">{formatPaise(r.purchases.totalPaise)}</td>
            </tr>
          ))}
          <tr className="border-t-2 border-line font-semibold">
            <td className="px-4 py-2">Total</td>
            <td className="px-2 py-2 text-right font-mono">{totals.billsCount}</td>
            <td className="px-2 py-2 text-right font-mono">{formatPaise(totals.grossPaise)}</td>
            <td className="px-2 py-2 text-right font-mono">{formatPaise(totals.discountPaise)}</td>
            <td className="px-2 py-2 text-right font-mono">{formatPaise(totals.taxablePaise)}</td>
            <td className="px-2 py-2 text-right font-mono">
              {formatPaise(totals.cgstPaise + totals.sgstPaise + totals.igstPaise)}
            </td>
            <td className="px-2 py-2 text-right font-mono">—</td>
            <td className="px-4 py-2 text-right font-mono">{formatPaise(totals.totalPaise)}</td>
            <td className="px-4 py-2 text-right font-mono">—</td>
          </tr>
        </tbody>
      </table>
    </div>
  );
}

function HsnTable({ rows }: { rows: HsnRow[] }) {
  return (
    <div className="overflow-x-auto rounded-xl border border-line bg-surface">
      <table className="w-full min-w-[720px] border-collapse text-sm">
        <thead>
          <tr className="text-left text-xs uppercase tracking-wider text-muted">
            <th className="px-4 py-2">HSN</th>
            <th className="px-2 py-2">GST</th>
            <th className="px-2 py-2">Unit</th>
            <th className="px-2 py-2 text-right">Qty</th>
            <th className="px-2 py-2 text-right">Taxable</th>
            <th className="px-2 py-2 text-right">CGST</th>
            <th className="px-2 py-2 text-right">SGST</th>
            <th className="px-2 py-2 text-right">IGST</th>
            <th className="px-4 py-2 text-right">Total</th>
          </tr>
        </thead>
        <tbody>
          {rows.length === 0 ? (
            <tr>
              <td colSpan={9} className="px-4 py-6 text-center text-muted">
                No lines in this range
              </td>
            </tr>
          ) : (
            rows.map((r) => (
              <tr key={`${r.hsn}-${r.gstRateBp}-${r.unit}`} className="border-t border-line-soft">
                <td className="px-4 py-2 font-mono">{r.hsn || "—"}</td>
                <td className="px-2 py-2">{formatGstRate(r.gstRateBp)}</td>
                <td className="px-2 py-2">{r.unit}</td>
                <td className="px-2 py-2 text-right font-mono">{formatQty(r.qtyMilli, r.unit)}</td>
                <td className="px-2 py-2 text-right font-mono">{formatPaise(r.taxablePaise)}</td>
                <td className="px-2 py-2 text-right font-mono">{formatPaise(r.cgstPaise)}</td>
                <td className="px-2 py-2 text-right font-mono">{formatPaise(r.sgstPaise)}</td>
                <td className="px-2 py-2 text-right font-mono">{formatPaise(r.igstPaise)}</td>
                <td className="px-4 py-2 text-right font-mono font-medium">{formatPaise(r.totalPaise)}</td>
              </tr>
            ))
          )}
        </tbody>
      </table>
    </div>
  );
}

function LayoutToggle({ layout, onChange }: { layout: "80mm" | "a4"; onChange: (l: "80mm" | "a4") => void }) {
  return (
    <div className="flex overflow-hidden rounded-md border border-line text-[12px] font-semibold">
      <button type="button" className={`px-3 py-1.5 ${layout === "80mm" ? "bg-brand text-white" : "bg-surface"}`} onClick={() => onChange("80mm")}>
        80 mm
      </button>
      <button type="button" className={`px-3 py-1.5 ${layout === "a4" ? "bg-brand text-white" : "bg-surface"}`} onClick={() => onChange("a4")}>
        A4
      </button>
    </div>
  );
}

function downloadCsv(filename: string, content: string) {
  const blob = new Blob([content], { type: "text/csv;charset=utf-8" });
  const url = URL.createObjectURL(blob);
  const a = document.createElement("a");
  a.href = url;
  a.download = filename;
  a.click();
  URL.revokeObjectURL(url);
}
