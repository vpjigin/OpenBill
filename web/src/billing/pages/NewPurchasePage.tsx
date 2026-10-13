import { useEffect, useMemo, useState } from "react";
import type { FormEvent } from "react";
import { Link, useNavigate } from "react-router-dom";
import { formatPaise, inputToQtyMilli, isDecimalUnit, rupeesToPaise } from "../../shared/money";
import { FormPageWrap, StatCard, StatGrid } from "../../shared/layout";
import { Button, ErrorNote, Field, PageHeader, errorText } from "../../shared/ui";
import { billingApi, type Item, type Supplier } from "../billingApi";
import { previewTotals } from "../gst";

interface LineDraft {
  item: Item | null;
  qty: string;
  cost: string;
  gst: number;
}

const emptyLine = (): LineDraft => ({ item: null, qty: "1", cost: "", gst: 12 });

export function NewPurchasePage() {
  const navigate = useNavigate();
  const [shopState, setShopState] = useState<string | null>(null);
  const [supplierQ, setSupplierQ] = useState("");
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [supplier, setSupplier] = useState<Supplier | null>(null);
  const [billNo, setBillNo] = useState("");
  const [billDate, setBillDate] = useState(() => new Date().toISOString().slice(0, 10));
  const [notes, setNotes] = useState("");
  const [itemQ, setItemQ] = useState("");
  const [items, setItems] = useState<Item[]>([]);
  const [lines, setLines] = useState<LineDraft[]>([emptyLine()]);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);

  useEffect(() => {
    billingApi.shop().then((s) => setShopState(s.stateCode)).catch(() => undefined);
  }, []);

  useEffect(() => {
    if (!supplierQ.trim()) {
      setSuppliers([]);
      return;
    }
    billingApi
      .suppliers({ q: supplierQ, size: 20 })
      .then((r) => setSuppliers(r.items))
      .catch(() => setSuppliers([]));
  }, [supplierQ]);

  useEffect(() => {
    if (!itemQ.trim()) {
      setItems([]);
      return;
    }
    billingApi.items({ q: itemQ, size: 20 }).then((r) => setItems(r.items)).catch(() => setItems([]));
  }, [itemQ]);

  const interState = useMemo(() => {
    if (!shopState || !supplier?.stateCode) return false;
    return shopState !== supplier.stateCode;
  }, [shopState, supplier]);

  const preview = useMemo(() => {
    const pl = lines
      .filter((l) => l.item)
      .map((l) => {
        const costPaise = rupeesToPaise(l.cost) ?? 0;
        const qtyMilli = inputToQtyMilli(l.qty, l.item!.unit) ?? 0;
        return {
          ratePaise: costPaise,
          qtyMilli,
          gstRateBp: Math.round(l.gst * 100),
          taxInclusive: false,
          discountPaise: 0,
        };
      })
      .filter((l) => l.qtyMilli > 0);
    if (pl.length === 0) return null;
    return previewTotals(pl, interState, 0);
  }, [lines, interState]);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (!supplier) {
      setError("Choose a supplier");
      return;
    }
    setError(null);
    setBusy(true);
    try {
      const bodyLines = lines
        .filter((l) => l.item)
        .map((l) => {
          const costPaise = rupeesToPaise(l.cost);
          const qtyMilli = inputToQtyMilli(l.qty, l.item!.unit);
          if (costPaise === null || qtyMilli === null || qtyMilli <= 0) {
            throw new Error("Check quantities and costs on every line");
          }
          return {
            itemId: l.item!.id,
            qtyMilli,
            costPaise,
            gstRateBp: Math.round(l.gst * 100),
          };
        });
      if (bodyLines.length === 0) throw new Error("Add at least one line");
      const p = await billingApi.createPurchase({
        supplierId: supplier.id,
        billNo,
        billDate,
        notes: notes || null,
        lines: bodyLines,
      });
      navigate(`/purchases/${p.id}`);
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <FormPageWrap>
      <PageHeader title="New purchase" subtitle="Record a supplier bill and add stock" />
      <ErrorNote message={error} />
      <form className="flex w-full min-w-0 flex-col gap-4" onSubmit={(e) => void submit(e)}>
        <div className="grid gap-3 sm:grid-cols-2">
          <Field label="Supplier bill no" value={billNo} onChange={(e) => setBillNo(e.target.value)} required />
          <label className="flex flex-col gap-1 text-sm">
            <span className="text-muted">Bill date</span>
            <input
              type="date"
              required
              className="rounded-lg border border-line px-2 py-2"
              value={billDate}
              onChange={(e) => setBillDate(e.target.value)}
            />
          </label>
        </div>
        <div>
          <label className="mb-1 block text-sm text-muted">Supplier</label>
          {supplier ? (
            <div className="flex items-center justify-between rounded-lg border border-line px-3 py-2">
              <span>{supplier.name}</span>
              <button type="button" className="text-sm text-brand" onClick={() => setSupplier(null)}>
                Change
              </button>
            </div>
          ) : (
            <>
              <input
                type="search"
                placeholder="Search supplier"
                className="mb-2 w-full rounded-lg border border-line px-3 py-2 text-sm"
                value={supplierQ}
                onChange={(e) => setSupplierQ(e.target.value)}
              />
              <ul className="m-0 max-h-40 list-none overflow-auto rounded-lg border border-line">
                {suppliers.map((s) => (
                  <li key={s.id}>
                    <button
                      type="button"
                      className="w-full px-3 py-2 text-left text-sm hover:bg-ground"
                      onClick={() => {
                        setSupplier(s);
                        setSupplierQ("");
                        setSuppliers([]);
                      }}
                    >
                      {s.name}
                    </button>
                  </li>
                ))}
              </ul>
            </>
          )}
        </div>
        <Field label="Notes" value={notes} onChange={(e) => setNotes(e.target.value)} />

        <div className="rounded-xl border border-line bg-surface p-4">
          <h2 className="mt-0 text-base font-semibold">Lines</h2>
          {lines.map((line, idx) => (
            <div key={idx} className="mb-4 border-b border-line-soft pb-4 last:mb-0 last:border-0">
              <input
                type="search"
                placeholder="Search item"
                className="mb-2 w-full rounded-lg border border-line px-3 py-2 text-sm"
                value={line.item ? line.item.name : itemQ}
                onChange={(e) => {
                  setItemQ(e.target.value);
                  if (line.item) setLines((ls) => ls.map((l, i) => (i === idx ? { ...l, item: null } : l)));
                }}
              />
              {line.item ? (
                <p className="text-sm text-muted">
                  {line.item.name} · {line.item.unit}
                </p>
              ) : (
                items.length > 0 && (
                  <ul className="mb-2 max-h-32 list-none overflow-auto rounded border border-line">
                    {items.map((it) => (
                      <li key={it.id}>
                        <button
                          type="button"
                          className="w-full px-2 py-1.5 text-left text-sm hover:bg-ground"
                          onClick={() => {
                            setLines((ls) =>
                              ls.map((l, i) =>
                                i === idx
                                  ? {
                                      ...l,
                                      item: it,
                                      cost: it.costPaise ? (it.costPaise / 100).toFixed(2) : "",
                                      gst: it.gstRateBp / 100,
                                    }
                                  : l,
                              ),
                            );
                            setItemQ("");
                            setItems([]);
                          }}
                        >
                          {it.name}
                        </button>
                      </li>
                    ))}
                  </ul>
                )
              )}
              <div className="mt-2 grid gap-2 sm:grid-cols-3">
                <Field
                  label={`Qty${line.item && isDecimalUnit(line.item.unit) ? " (decimal ok)" : ""}`}
                  value={line.qty}
                  onChange={(e) =>
                    setLines((ls) => ls.map((l, i) => (i === idx ? { ...l, qty: e.target.value } : l)))
                  }
                />
                <Field
                  label="Cost (₹, before GST)"
                  value={line.cost}
                  onChange={(e) =>
                    setLines((ls) => ls.map((l, i) => (i === idx ? { ...l, cost: e.target.value } : l)))
                  }
                />
                <label className="flex flex-col gap-1 text-sm">
                  <span className="text-muted">GST %</span>
                  <select
                    className="rounded-lg border border-line px-2 py-2"
                    value={line.gst}
                    onChange={(e) =>
                      setLines((ls) => ls.map((l, i) => (i === idx ? { ...l, gst: Number(e.target.value) } : l)))
                    }
                  >
                    {[0, 5, 12, 18, 28].map((g) => (
                      <option key={g} value={g}>
                        {g}%
                      </option>
                    ))}
                  </select>
                </label>
              </div>
            </div>
          ))}
          <Button type="button" variant="secondary" onClick={() => setLines((ls) => [...ls, emptyLine()])}>
            Add line
          </Button>
        </div>

        {preview && (
          <div>
            <div className="mb-2 text-sm font-medium">Preview {interState ? "(inter-state)" : "(intra-state)"}</div>
            <StatGrid>
              <StatCard label="Taxable" value={formatPaise(preview.taxable)} />
              <StatCard label="Input GST" value={formatPaise(preview.cgst + preview.sgst + preview.igst)} />
              <StatCard label="Total" value={formatPaise(preview.total)} />
            </StatGrid>
          </div>
        )}

        <div className="flex flex-wrap gap-2">
          <Button type="submit" className="min-h-11" disabled={busy}>
            {busy ? "Saving…" : "Save purchase"}
          </Button>
          <Link to="/purchases" className="inline-flex items-center text-sm text-brand">
            Cancel
          </Link>
        </div>
      </form>
    </FormPageWrap>
  );
}
