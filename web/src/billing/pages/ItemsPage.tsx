import { useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";
import { useNavigate } from "react-router-dom";
import {
  formatGstRate,
  formatPaise,
  formatQty,
  inputToQtyMilli,
  isDecimalUnit,
  paiseToRupeesInput,
  qtyMilliToInput,
  rupeesToPaise,
} from "../../shared/money";
import { Button, Card, ErrorNote, Field, ListPager, PageHeader, errorText } from "../../shared/ui";
import { billingApi, type Item } from "../billingApi";

const PAGE_SIZE = 50;
const GST_RATES = [0, 5, 12, 18, 28];
const UNITS = ["NOS", "PCS", "BOX", "KG", "G", "LTR", "ML", "MTR"] as const;

const empty = {
  name: "",
  code: "",
  barcode: "",
  hsn: "",
  unit: "NOS",
  rate: "",
  gst: 12,
  stock: "0",
  reorder: "0",
  taxInclusive: false,
};

function isLowStock(i: Item): boolean {
  return i.reorderLevelMilli > 0 && i.stockMilli <= i.reorderLevelMilli;
}

export function ItemsPage() {
  const navigate = useNavigate();
  const [items, setItems] = useState<Item[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [q, setQ] = useState("");
  const [debouncedQ, setDebouncedQ] = useState("");
  const [lowStockOnly, setLowStockOnly] = useState(false);
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [form, setForm] = useState(empty);
  const [editing, setEditing] = useState<Item | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const t = setTimeout(() => setDebouncedQ(q.trim()), 250);
    return () => clearTimeout(t);
  }, [q]);

  useEffect(() => {
    setPage(0);
  }, [debouncedQ, lowStockOnly]);

  const load = useCallback(() => {
    if (lowStockOnly) {
      billingApi
        .lowStockItems()
        .then((list) => {
          setItems(list);
          setTotal(list.length);
        })
        .catch((e) => setError(errorText(e)));
      return;
    }
    billingApi
      .items({ q: debouncedQ || undefined, page, size: PAGE_SIZE })
      .then((r) => {
        setItems(r.items);
        setTotal(r.total);
      })
      .catch((e) => setError(errorText(e)));
  }, [debouncedQ, page, lowStockOnly]);
  useEffect(load, [load]);

  function toggleSelected(id: string) {
    setSelected((prev) => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  }

  function toggleAllVisible() {
    if (selected.size === items.length) setSelected(new Set());
    else setSelected(new Set(items.map((i) => i.id)));
  }

  async function generateBarcode(i: Item) {
    setError(null);
    try {
      const updated = await billingApi.generateItemBarcode(i.id);
      setItems((list) => list.map((row) => (row.id === updated.id ? updated : row)));
      if (editing?.id === updated.id) {
        setEditing(updated);
        setForm((f) => ({ ...f, barcode: updated.barcode ?? "" }));
      }
    } catch (err) {
      setError(errorText(err));
    }
  }

  function printLabels() {
    if (selected.size === 0) {
      setError("Select at least one item to print labels.");
      return;
    }
    navigate("/items/labels", { state: { itemIds: [...selected] } });
  }

  function edit(i: Item) {
    setEditing(i);
    setForm({
      name: i.name,
      code: i.code ?? "",
      barcode: i.barcode ?? "",
      hsn: i.hsn ?? "",
      unit: i.unit,
      rate: paiseToRupeesInput(i.ratePaise),
      gst: i.gstRateBp / 100,
      stock: qtyMilliToInput(i.stockMilli, i.unit),
      reorder: qtyMilliToInput(i.reorderLevelMilli, i.unit),
      taxInclusive: i.taxInclusive,
    });
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    const ratePaise = rupeesToPaise(form.rate);
    if (ratePaise === null) {
      setError("Enter the rate like 450 or 450.50");
      return;
    }
    const stockMilli = inputToQtyMilli(form.stock, form.unit);
    if (stockMilli === null) {
      setError(isDecimalUnit(form.unit) ? "Enter stock with up to 3 decimals" : "Enter whole-number stock");
      return;
    }
    const reorderLevelMilli = inputToQtyMilli(form.reorder, form.unit);
    if (reorderLevelMilli === null) {
      setError(isDecimalUnit(form.unit) ? "Enter reorder level with up to 3 decimals" : "Enter whole-number reorder level");
      return;
    }
    const body = {
      name: form.name,
      code: form.code,
      barcode: form.barcode,
      hsn: form.hsn,
      unit: form.unit,
      ratePaise,
      gstRateBp: Math.round(form.gst * 100),
      stockMilli,
      reorderLevelMilli,
      taxInclusive: form.taxInclusive,
      version: editing?.version ?? null,
    };
    try {
      if (editing) await billingApi.updateItem(editing.id, body);
      else await billingApi.createItem(body);
      setForm(empty);
      setEditing(null);
      load();
    } catch (err) {
      setError(errorText(err));
    }
  }

  return (
    <>
      <PageHeader
        title="Items & stock"
        subtitle="Rates are before GST unless “Price includes GST” is checked."
      />
      <ErrorNote message={error} />
      <div className="flex w-full min-w-0 flex-col gap-5 xl:flex-row xl:items-stretch">
        <Card
          className="min-w-0 flex-[999_1_520px]"
          title="Items"
          actions={
            <div className="flex flex-wrap items-center gap-2">
              {!lowStockOnly && (
                <span className="text-[13px] text-muted">
                  {total === 1 ? "1 item" : `${total.toLocaleString("en-IN")} items`}
                </span>
              )}
              <Button
                className="min-h-9 px-3 text-[13px]"
                variant="primary"
                disabled={selected.size === 0}
                onClick={printLabels}
              >
                Print labels ({selected.size})
              </Button>
              <label className="flex cursor-pointer items-center gap-2 text-[13px]">
                <input
                  type="checkbox"
                  checked={lowStockOnly}
                  onChange={(e) => setLowStockOnly(e.target.checked)}
                  className="size-4 accent-brand"
                />
                Low stock only
              </label>
              <input
                type="search"
                aria-label="Search items"
                placeholder="Search"
                value={q}
                disabled={lowStockOnly}
                onChange={(e) => setQ(e.target.value)}
                className="min-h-10 w-48 rounded-lg border border-[#b9c2bd] px-3 text-sm disabled:opacity-50"
              />
            </div>
          }
        >
          <div className="flex flex-col gap-2 px-2 pb-2 md:hidden">
            {items.map((i) => (
              <div
                key={i.id}
                className={`rounded-xl border border-line bg-surface p-4 ${isLowStock(i) ? "bg-amber-500/10" : ""}`}
              >
                <div className="flex items-start justify-between gap-2">
                  <label className="flex min-w-0 flex-1 cursor-pointer items-start gap-2">
                    <input
                      type="checkbox"
                      checked={selected.has(i.id)}
                      onChange={() => toggleSelected(i.id)}
                      aria-label={`Select ${i.name}`}
                      className="mt-1 size-5 shrink-0 accent-brand"
                    />
                    <span className="min-w-0 font-semibold">{i.name}</span>
                  </label>
                  <span className="shrink-0 font-mono text-sm">{formatPaise(i.ratePaise)}</span>
                </div>
                <p className="mt-1 text-xs text-muted">
                  {i.unit} · HSN {i.hsn ?? "—"} · Stock {formatQty(i.stockMilli, i.unit)}
                </p>
                <div className="mt-3 flex flex-wrap gap-2">
                  <Button className="min-h-11 px-3 text-[13px]" onClick={() => edit(i)}>
                    Edit
                  </Button>
                </div>
              </div>
            ))}
          </div>
          <div className="hidden md:block">
            <table className="w-full border-collapse text-sm">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wider text-muted">
                  <th className="w-10 px-3 py-2.5 font-medium">
                    <input
                      type="checkbox"
                      aria-label="Select all"
                      checked={items.length > 0 && selected.size === items.length}
                      onChange={toggleAllVisible}
                      className="size-4 accent-brand"
                    />
                  </th>
                  <th className="px-3 py-2.5 font-medium">Name</th>
                  <th className="px-3 py-2.5 font-medium">Unit</th>
                  <th className="px-3 py-2.5 font-medium">HSN</th>
                  <th className="px-3 py-2.5 text-right font-medium">Rate</th>
                  <th className="px-3 py-2.5 text-right font-medium">Cost</th>
                  <th className="px-3 py-2.5 text-right font-medium">Margin</th>
                  <th className="px-3 py-2.5 text-right font-medium">GST</th>
                  <th className="px-3 py-2.5 text-right font-medium">Stock</th>
                  <th className="px-5 py-2.5 text-right font-medium">Action</th>
                </tr>
              </thead>
              <tbody>
                {items.map((i) => (
                  <tr
                    key={i.id}
                    className={`border-t border-line-soft ${isLowStock(i) ? "bg-amber-500/10" : ""}`}
                  >
                    <td className="px-3 py-3">
                      <input
                        type="checkbox"
                        checked={selected.has(i.id)}
                        onChange={() => toggleSelected(i.id)}
                        aria-label={`Select ${i.name}`}
                        className="size-4 accent-brand"
                      />
                    </td>
                    <td className="px-3 py-3 font-medium">
                      {i.name}
                      {i.taxInclusive && <span className="ml-1.5 text-[11px] text-muted">incl. GST</span>}
                    </td>
                    <td className="px-3 py-3 font-mono text-[13px]">{i.unit}</td>
                    <td className="px-3 py-3 font-mono text-[13px]">{i.hsn ?? "—"}</td>
                    <td className="px-3 py-3 text-right font-mono">{formatPaise(i.ratePaise)}</td>
                    <td className="px-3 py-3 text-right font-mono">{formatPaise(i.costPaise)}</td>
                    <td className="px-3 py-3 text-right font-mono text-[13px]">
                      {i.ratePaise > 0 && i.costPaise > 0
                        ? `${(((i.ratePaise - i.costPaise) / i.ratePaise) * 100).toFixed(1)}%`
                        : "—"}
                    </td>
                    <td className="px-3 py-3 text-right">{formatGstRate(i.gstRateBp)}</td>
                    <td
                      className={`px-3 py-3 text-right font-mono ${
                        isLowStock(i) ? "font-semibold text-amber-800" : i.stockMilli <= 0 ? "text-danger" : ""
                      }`}
                    >
                      {formatQty(i.stockMilli, i.unit)} {i.unit}
                      {isLowStock(i) && i.reorderLevelMilli > 0 && (
                        <span className="block text-[11px] font-normal text-muted">
                          reorder {formatQty(i.reorderLevelMilli, i.unit)}
                        </span>
                      )}
                    </td>
                    <td className="px-5 py-3 text-right">
                      <div className="flex flex-wrap justify-end gap-1">
                        {!i.barcode && (
                          <Button className="min-h-9 px-2 text-[12px]" onClick={() => void generateBarcode(i)}>
                            Generate barcode
                          </Button>
                        )}
                        <Button className="min-h-9 px-3 text-[13px]" onClick={() => edit(i)}>
                          Edit
                        </Button>
                      </div>
                    </td>
                  </tr>
                ))}
                {items.length === 0 && (
                  <tr className="border-t border-line-soft">
                    <td colSpan={10} className="px-5 py-4 text-muted">
                      {lowStockOnly
                        ? "No low-stock items."
                        : debouncedQ
                          ? "No matches."
                          : "No items yet. Add your first item on the right."}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
          {!lowStockOnly && <ListPager page={page} size={PAGE_SIZE} total={total} onPage={setPage} />}
        </Card>

        <form onSubmit={submit} className="flex min-w-0 flex-[1_1_300px] flex-col gap-4 rounded-xl border border-line bg-surface p-5">
          <h2 className="m-0 text-[17px] font-semibold">{editing ? "Edit item" : "Add item"}</h2>
          <Field label="Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          <div className="grid grid-cols-2 gap-3">
            <Field
              label={form.taxInclusive ? "Rate (₹, incl. GST)" : "Rate (₹, before GST)"}
              inputMode="decimal"
              value={form.rate}
              onChange={(e) => setForm({ ...form, rate: e.target.value })}
              required
            />
            <div className="flex flex-col gap-1.5">
              <label htmlFor="gst" className="text-[13px] font-medium">GST</label>
              <select
                id="gst"
                value={form.gst}
                onChange={(e) => setForm({ ...form, gst: Number(e.target.value) })}
                className="min-h-11 rounded-lg border border-[#b9c2bd] bg-surface px-3 text-sm"
              >
                {GST_RATES.map((r) => (
                  <option key={r} value={r}>
                    {r}%
                  </option>
                ))}
              </select>
            </div>
          </div>
          <label className="flex cursor-pointer items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={form.taxInclusive}
              onChange={(e) => setForm({ ...form, taxInclusive: e.target.checked })}
              className="size-4 rounded border-[#b9c2bd]"
            />
            Price includes GST
          </label>
          <div className="grid grid-cols-2 gap-3">
            <Field label="HSN" value={form.hsn} onChange={(e) => setForm({ ...form, hsn: e.target.value })} />
            <div className="flex flex-col gap-1.5">
              <label htmlFor="unit" className="text-[13px] font-medium">Unit</label>
              <select
                id="unit"
                value={form.unit}
                onChange={(e) => setForm({ ...form, unit: e.target.value })}
                className="min-h-11 rounded-lg border border-[#b9c2bd] bg-surface px-3 text-sm"
              >
                {UNITS.map((u) => (
                  <option key={u} value={u}>
                    {u}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div className="grid grid-cols-2 gap-3">
            <Field label="Code" value={form.code} onChange={(e) => setForm({ ...form, code: e.target.value })} />
            <Field label="Barcode" value={form.barcode} onChange={(e) => setForm({ ...form, barcode: e.target.value })} />
          </div>
          {editing && !editing.barcode && (
            <Button type="button" onClick={() => void generateBarcode(editing)}>
              Generate barcode
            </Button>
          )}
          <div className="grid grid-cols-2 gap-3">
            <Field
              label={`Stock (${form.unit})`}
              inputMode={isDecimalUnit(form.unit) ? "decimal" : "numeric"}
              value={form.stock}
              onChange={(e) => setForm({ ...form, stock: e.target.value })}
            />
            <Field
              label={`Reorder level (${form.unit})`}
              inputMode={isDecimalUnit(form.unit) ? "decimal" : "numeric"}
              value={form.reorder}
              onChange={(e) => setForm({ ...form, reorder: e.target.value })}
            />
          </div>
          <p className="m-0 text-xs text-muted">Set reorder level to 0 to turn off low-stock alerts for this item.</p>
          <div className="flex gap-2">
            <Button type="submit" variant="primary" className="flex-1">
              {editing ? "Save changes" : "Add item"}
            </Button>
            {editing && (
              <Button onClick={() => { setEditing(null); setForm(empty); }}>
                Cancel
              </Button>
            )}
          </div>
        </form>
      </div>
    </>
  );
}
