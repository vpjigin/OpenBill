import { useEffect, useMemo, useState } from "react";
import { Link, useLocation, useNavigate } from "react-router-dom";
import { ean13Svg } from "../../shared/barcode";
import { formatPaise } from "../../shared/money";
import { Button, ErrorNote, PageHeader, errorText } from "../../shared/ui";
import { billingApi, type Item, type ShopInfo } from "../billingApi";
import { previewTotals } from "../gst";

export type LabelLayoutId = "a4-65" | "a4-24" | "roll-50x25";

const LAYOUTS: { id: LabelLayoutId; label: string; cols: number; rows: number; wMm: number; hMm: number }[] = [
  { id: "a4-65", label: "A4 · 65 labels (38.1 × 21.2 mm)", cols: 5, rows: 13, wMm: 38.1, hMm: 21.2 },
  { id: "a4-24", label: "A4 · 24 labels (64 × 34 mm)", cols: 3, rows: 8, wMm: 64, hMm: 34 },
  { id: "roll-50x25", label: "Single label 50 × 25 mm (roll)", cols: 1, rows: 1, wMm: 50, hMm: 25 },
];

export interface LabelPrintState {
  itemIds: string[];
}

function truncate(s: string, max: number): string {
  return s.length <= max ? s : `${s.slice(0, max - 1)}…`;
}

function priceInclGstPaise(item: Item): number {
  const t = previewTotals(
    [{ ratePaise: item.ratePaise, qtyMilli: 1000, gstRateBp: item.gstRateBp, taxInclusive: item.taxInclusive }],
    false,
  );
  return t.total;
}

function stockLabelQty(item: Item): number {
  if (item.stockMilli <= 0) return 1;
  return Math.max(1, Math.ceil(item.stockMilli / 1000));
}

function LabelCell({ shopName, item }: { shopName: string; item: Item }) {
  const code = item.barcode ?? "";
  const price = priceInclGstPaise(item);
  return (
    <div className="label-cell flex h-full flex-col justify-between overflow-hidden border border-dashed border-[#ccc] p-[1.2mm] text-[8pt] leading-tight">
      <div className="text-[6pt] text-[#444]">{truncate(shopName, 28)}</div>
      <div className="truncate text-[7.5pt] font-semibold">{truncate(item.name, 32)}</div>
      <div className="text-[9pt] font-bold">{formatPaise(price)}</div>
      {code.length === 13 ? (
        <div className="flex flex-col items-center gap-0.5">
          <div
            className="w-full max-w-full [&_svg]:mx-auto [&_svg]:h-auto [&_svg]:max-h-[9mm] [&_svg]:w-full"
            dangerouslySetInnerHTML={{ __html: ean13Svg(code, { width: 100, barHeight: 28 }) }}
          />
          <div className="font-mono text-[7pt] tracking-wider">{code}</div>
        </div>
      ) : (
        <div className="text-[7pt] text-danger">No barcode</div>
      )}
    </div>
  );
}

export function LabelPrintPage() {
  const nav = useNavigate();
  const location = useLocation();
  const state = location.state as LabelPrintState | null;
  const [items, setItems] = useState<Item[]>([]);
  const [shop, setShop] = useState<ShopInfo | null>(null);
  const [qtyById, setQtyById] = useState<Record<string, number>>({});
  const [layoutId, setLayoutId] = useState<LabelLayoutId>("a4-65");
  const [error, setError] = useState<string | null>(null);

  const layout = LAYOUTS.find((l) => l.id === layoutId)!;

  useEffect(() => {
    if (!state?.itemIds?.length) {
      nav("/items", { replace: true });
      return;
    }
    Promise.all([billingApi.items(), billingApi.shop()])
      .then(([all, s]) => {
        const picked = all.filter((i) => state.itemIds.includes(i.id));
        if (picked.length === 0) throw new Error("Selected items not found");
        setItems(picked);
        setShop(s);
        const q: Record<string, number> = {};
        for (const i of picked) q[i.id] = 1;
        setQtyById(q);
      })
      .catch((e) => setError(errorText(e)));
  }, [state, nav]);

  const expanded = useMemo(() => {
    const out: Item[] = [];
    for (const item of items) {
      const n = Math.max(0, Math.min(999, qtyById[item.id] ?? 1));
      for (let k = 0; k < n; k++) out.push(item);
    }
    return out;
  }, [items, qtyById]);

  const pages = useMemo(() => {
    const perPage = layout.cols * layout.rows;
    const chunks: Item[][] = [];
    for (let i = 0; i < expanded.length; i += perPage) {
      chunks.push(expanded.slice(i, i + perPage));
    }
    return chunks.length ? chunks : [[]];
  }, [expanded, layout]);

  const pageStyle =
    layoutId === "roll-50x25"
      ? "@page { size: 50mm 25mm; margin: 0; }"
      : "@page { size: A4; margin: 0; }";

  function fillFromStock() {
    const q: Record<string, number> = {};
    for (const i of items) q[i.id] = stockLabelQty(i);
    setQtyById(q);
  }

  if (!state?.itemIds?.length) return null;

  return (
    <>
      <style>{`
        ${pageStyle}
        @media print {
          .label-sheet-page { break-after: page; page-break-after: always; }
          .label-sheet-page:last-child { break-after: auto; page-break-after: auto; }
          .label-cell { border-color: transparent !important; }
        }
        .label-grid-${layoutId} {
          display: grid;
          grid-template-columns: repeat(${layout.cols}, ${layout.wMm}mm);
          grid-auto-rows: ${layout.hMm}mm;
          width: ${layoutId === "roll-50x25" ? layout.wMm + "mm" : "210mm"};
          gap: 0;
        }
      `}</style>
      <div className="no-print">
        <PageHeader title="Print labels" subtitle="Choose layout and quantities, then print or save as PDF." />
        <ErrorNote message={error} />
        <div className="mb-4 flex flex-wrap items-center gap-3">
          <Link to="/items" className="text-sm text-brand">
            ← Back to items
          </Link>
          <Button variant="primary" onClick={() => window.print()}>
            Print
          </Button>
        </div>
        <div className="mb-6 grid max-w-3xl gap-4 rounded-xl border border-line bg-surface p-4">
          <div className="flex flex-col gap-1.5">
            <label className="text-[13px] font-medium" htmlFor="layout">
              Layout
            </label>
            <select
              id="layout"
              value={layoutId}
              onChange={(e) => setLayoutId(e.target.value as LabelLayoutId)}
              className="min-h-11 rounded-lg border border-[#b9c2bd] bg-surface px-3 text-sm"
            >
              {LAYOUTS.map((l) => (
                <option key={l.id} value={l.id}>
                  {l.label}
                </option>
              ))}
            </select>
          </div>
          <div className="flex flex-wrap gap-2">
            <Button onClick={fillFromStock}>Set qty from stock</Button>
          </div>
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="text-left text-xs uppercase text-muted">
                <th className="py-2 font-medium">Item</th>
                <th className="py-2 font-medium">Barcode</th>
                <th className="py-2 text-right font-medium">Labels</th>
              </tr>
            </thead>
            <tbody>
              {items.map((i) => (
                <tr key={i.id} className="border-t border-line-soft">
                  <td className="py-2">{i.name}</td>
                  <td className="py-2 font-mono text-[13px]">{i.barcode ?? "—"}</td>
                  <td className="py-2 text-right">
                    <input
                      type="number"
                      min={0}
                      max={999}
                      className="w-20 rounded border border-[#b9c2bd] px-2 py-1 text-right font-mono"
                      value={qtyById[i.id] ?? 1}
                      onChange={(e) =>
                        setQtyById({ ...qtyById, [i.id]: Math.max(0, Number(e.target.value) || 0) })
                      }
                    />
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <p className="m-0 text-xs text-muted">
            {expanded.length} label{expanded.length === 1 ? "" : "s"} on {pages.length} sheet
            {pages.length === 1 ? "" : "s"}.
          </p>
        </div>
      </div>

      <div className="print-only hidden bg-white">
        {pages.map((pageItems, pi) => (
          <div key={pi} className={`label-sheet-page ${layoutId === "roll-50x25" ? "" : "mx-auto"}`}>
            <div className={`label-grid-${layoutId}`}>
              {pageItems.map((item, idx) => (
                <LabelCell key={`${item.id}-${pi}-${idx}`} shopName={shop?.shopName ?? "Shop"} item={item} />
              ))}
            </div>
          </div>
        ))}
      </div>
    </>
  );
}
