import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import type { KeyboardEvent } from "react";
import type { CurrentUser } from "../../shared/api";
import { formatGstRate, formatPaise, formatQty, isDecimalUnit, parseQty } from "../../shared/money";
import { Button, ErrorNote, Field, errorText } from "../../shared/ui";
import { gstStateName, stateCodeFromGstin } from "../../shared/gstStates";
import {
  billingApi,
  type Customer,
  type HeldBillSummary,
  type Item,
  type PaymentMode,
  type ShopInfo,
} from "../billingApi";
import { HeldBillsPanel } from "../HeldBillsPanel";
import { parseHeldBillPayload, type HeldBillPayloadV1 } from "../heldBillPayload";
import {
  discountInputToPaise,
  lineGross,
  previewTotals,
  type DiscountMode,
} from "../gst";

interface DiscountEntry {
  value: string;
  mode: DiscountMode;
}

interface CartLine {
  item: Item;
  qtyMilli: number;
  discount: DiscountEntry;
}

const emptyDiscount = (): DiscountEntry => ({ value: "", mode: "rupee" });

const emptyInline = () => ({ name: "", phone: "", gstin: "", stateCode: "", address: "" });

export function NewBillPage({ user }: { user: CurrentUser }) {
  const navigate = useNavigate();
  const [shop, setShop] = useState<ShopInfo | null>(null);
  const [query, setQuery] = useState("");
  const [results, setResults] = useState<Item[]>([]);
  const [cart, setCart] = useState<CartLine[]>([]);
  const [billDiscount, setBillDiscount] = useState<DiscountEntry>(emptyDiscount());
  const [interState, setInterState] = useState(false);
  const [payment, setPayment] = useState<PaymentMode>("CASH");
  const [selectedCustomer, setSelectedCustomer] = useState<Customer | null>(null);
  const [customerQuery, setCustomerQuery] = useState("");
  const [customerResults, setCustomerResults] = useState<Customer[]>([]);
  const [showNewCustomer, setShowNewCustomer] = useState(false);
  const [inlineCustomer, setInlineCustomer] = useState(emptyInline());
  const [saveCustomer, setSaveCustomer] = useState(false);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [resumeNotice, setResumeNotice] = useState<string | null>(null);
  const [heldBills, setHeldBills] = useState<HeldBillSummary[]>([]);
  const [showHeldPanel, setShowHeldPanel] = useState(false);
  const [holding, setHolding] = useState(false);
  const [summaryOpen, setSummaryOpen] = useState(false);
  const searchRef = useRef<HTMLInputElement>(null);
  const customerSearchRef = useRef<HTMLInputElement>(null);
  const saveRef = useRef<() => void>(() => {});
  const holdRef = useRef<() => void>(() => {});

  useEffect(() => {
    billingApi.shop().then(setShop).catch(() => undefined);
  }, []);

  const refreshHeldBills = useCallback(() => {
    billingApi.heldBills().then(setHeldBills).catch(() => setHeldBills([]));
  }, []);

  useEffect(() => {
    refreshHeldBills();
    const t = setInterval(refreshHeldBills, 30_000);
    return () => clearInterval(t);
  }, [refreshHeldBills]);

  useEffect(() => {
    const q = query.trim();
    if (!q) {
      setResults([]);
      return;
    }
    const t = setTimeout(() => {
      billingApi.items({ q, size: 20 }).then((r) => setResults(r.items)).catch(() => setResults([]));
    }, 150);
    return () => clearTimeout(t);
  }, [query]);

  useEffect(() => {
    const q = customerQuery.trim();
    if (!q || selectedCustomer) {
      setCustomerResults([]);
      return;
    }
    const t = setTimeout(() => {
      billingApi.customers({ q, size: 20 }).then((r) => setCustomerResults(r.items)).catch(() => setCustomerResults([]));
    }, 150);
    return () => clearTimeout(t);
  }, [customerQuery, selectedCustomer]);

  useEffect(() => {
    const onKey = (e: globalThis.KeyboardEvent) => {
      if (e.key === "F2") {
        e.preventDefault();
        searchRef.current?.focus();
      }
      if (e.key === "F4") {
        e.preventDefault();
        customerSearchRef.current?.focus();
      }
      if (e.key === "F10") {
        e.preventDefault();
        saveRef.current();
      }
      if (e.key === "F8") {
        e.preventDefault();
        holdRef.current();
      }
    };
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  const customerStateCode = useMemo(() => {
    if (selectedCustomer?.stateCode) return selectedCustomer.stateCode;
    if (showNewCustomer) {
      const fromGstin = stateCodeFromGstin(inlineCustomer.gstin);
      if (fromGstin) return fromGstin;
      return inlineCustomer.stateCode || null;
    }
    return null;
  }, [selectedCustomer, showNewCustomer, inlineCustomer]);

  const autoPlaceOfSupply = Boolean(shop?.stateCode && customerStateCode);

  const effectiveInterState = useMemo(() => {
    if (autoPlaceOfSupply && shop?.stateCode && customerStateCode) {
      return shop.stateCode !== customerStateCode;
    }
    return interState;
  }, [autoPlaceOfSupply, shop?.stateCode, customerStateCode, interState]);

  function add(item: Item) {
    setCart((c) => {
      const i = c.findIndex((l) => l.item.id === item.id);
      const step = isDecimalUnit(item.unit) ? 250 : 1000;
      if (i >= 0) return c.map((l, j) => (j === i ? { ...l, qtyMilli: l.qtyMilli + step } : l));
      return [...c, { item, qtyMilli: step, discount: emptyDiscount() }];
    });
    setQuery("");
    setResults([]);
    searchRef.current?.focus();
  }

  async function onSearchKey(e: KeyboardEvent<HTMLInputElement>) {
    if (e.key !== "Enter") return;
    e.preventDefault();
    const q = query.trim();
    if (!q) return;
    const list = results.length
      ? results
      : await billingApi.items({ q, size: 20 }).then((r) => r.items).catch(() => []);
    const exact = list.find((i) => i.barcode === q || i.code === q);
    if (exact) add(exact);
    else if (list.length === 1) add(list[0]);
  }

  function setQtyMilli(id: string, qtyMilli: number) {
    setCart((c) =>
      qtyMilli <= 0 ? c.filter((l) => l.item.id !== id) : c.map((l) => (l.item.id === id ? { ...l, qtyMilli } : l)),
    );
  }

  function setLineDiscount(id: string, discount: DiscountEntry) {
    setCart((c) => c.map((l) => (l.item.id === id ? { ...l, discount } : l)));
  }

  function bumpQty(line: CartLine, delta: number) {
    setQtyMilli(line.item.id, line.qtyMilli + delta);
  }

  const pricedLines = useMemo(
    () =>
      cart.map((l) => {
        const gross = lineGross(l.item.ratePaise, l.qtyMilli);
        const discountPaise = discountInputToPaise(gross, l.discount.value, l.discount.mode);
        return { line: l, gross, discountPaise };
      }),
    [cart],
  );

  const billDiscountPaise = useMemo(() => {
    const netSubtotal = pricedLines.reduce(
      (sum, p) => sum + p.gross - (p.discountPaise ?? 0),
      0,
    );
    return discountInputToPaise(netSubtotal, billDiscount.value, billDiscount.mode);
  }, [pricedLines, billDiscount]);

  const totals = useMemo(
    () =>
      previewTotals(
        pricedLines.map((p) => ({
          ratePaise: p.line.item.ratePaise,
          qtyMilli: p.line.qtyMilli,
          gstRateBp: p.line.item.gstRateBp,
          taxInclusive: p.line.item.taxInclusive,
          discountPaise: p.discountPaise ?? 0,
        })),
        effectiveInterState,
        billDiscountPaise ?? 0,
      ),
    [pricedLines, effectiveInterState, billDiscountPaise],
  );

  const clearBill = useCallback(() => {
    setCart([]);
    setBillDiscount(emptyDiscount());
    setSelectedCustomer(null);
    setShowNewCustomer(false);
    setInlineCustomer(emptyInline());
    setSaveCustomer(false);
    setCustomerQuery("");
    setInterState(false);
    setPayment("CASH");
    setError(null);
    setResumeNotice(null);
  }, []);

  const defaultHoldLabel = useCallback(() => {
    if (selectedCustomer?.name) return selectedCustomer.name;
    const t = new Date().toLocaleTimeString("en-IN", { hour: "numeric", minute: "2-digit" });
    return `Bill ${t}`;
  }, [selectedCustomer]);

  const buildHoldPayload = useCallback((): HeldBillPayloadV1 => {
    return {
      v: 1,
      lines: cart.map((l) => ({
        itemId: l.item.id,
        qtyMilli: l.qtyMilli,
        discount: { ...l.discount },
      })),
      billDiscount: { ...billDiscount },
      interState,
      payment,
      selectedCustomerId: selectedCustomer?.id ?? null,
      inlineCustomer: showNewCustomer ? { ...inlineCustomer } : null,
      showNewCustomer,
      saveCustomer,
      previewTotalPaise: totals.total,
    };
  }, [
    cart,
    billDiscount,
    interState,
    payment,
    selectedCustomer,
    showNewCustomer,
    inlineCustomer,
    saveCustomer,
    totals.total,
  ]);

  const holdBill = useCallback(async () => {
    if (cart.length === 0 || holding) return;
    const suggested = defaultHoldLabel();
    const input = window.prompt("Label for this held bill (optional)", suggested);
    if (input === null) return;
    const label = input.trim() || suggested;
    setHolding(true);
    setError(null);
    try {
      const payload = JSON.stringify(buildHoldPayload());
      await billingApi.createHeldBill({ label, payload });
      clearBill();
      refreshHeldBills();
      searchRef.current?.focus();
    } catch (e) {
      setError(errorText(e));
    } finally {
      setHolding(false);
    }
  }, [cart.length, holding, defaultHoldLabel, buildHoldPayload, clearBill, refreshHeldBills]);

  const resumeHeld = useCallback(
    async (summary: HeldBillSummary) => {
      if (cart.length > 0) {
        window.alert("The current bill is not empty. Hold or clear it before resuming another bill.");
        return;
      }
      setError(null);
      setResumeNotice(null);
      try {
        const detail = await billingApi.heldBill(summary.id);
        const payload = parseHeldBillPayload(detail.payload);
        if (!payload) {
          setError("Could not read this held bill");
          return;
        }
        const catalog = await billingApi.itemsAll();
        const byId = new Map(catalog.map((i) => [i.id, i]));
        const nextCart: CartLine[] = [];
        let skipped = 0;
        for (const line of payload.lines) {
          const item = byId.get(line.itemId);
          if (!item) {
            skipped++;
            continue;
          }
          nextCart.push({
            item,
            qtyMilli: line.qtyMilli,
            discount: line.discount ?? emptyDiscount(),
          });
        }
        if (nextCart.length === 0) {
          setError("No items from this held bill are still available");
          return;
        }
        setCart(nextCart);
        setBillDiscount(payload.billDiscount ?? emptyDiscount());
        setInterState(payload.interState);
        setPayment(payload.payment);
        setSaveCustomer(payload.saveCustomer);
        if (payload.selectedCustomerId) {
          const c = await billingApi.customer(payload.selectedCustomerId).catch(() => null);
          setSelectedCustomer(c);
          setShowNewCustomer(false);
          setInlineCustomer(emptyInline());
        } else if (payload.showNewCustomer && payload.inlineCustomer) {
          setSelectedCustomer(null);
          setShowNewCustomer(true);
          setInlineCustomer(payload.inlineCustomer);
        } else {
          setSelectedCustomer(null);
          setShowNewCustomer(false);
          setInlineCustomer(emptyInline());
        }
        setCustomerQuery("");
        await billingApi.deleteHeldBill(summary.id);
        refreshHeldBills();
        setShowHeldPanel(false);
        if (skipped > 0) {
          setResumeNotice(`${skipped} line(s) skipped — item no longer exists. Prices refreshed from catalog.`);
        } else {
          setResumeNotice("Prices refreshed from catalog.");
        }
      } catch (e) {
        setError(errorText(e));
      }
    },
    [cart.length, refreshHeldBills],
  );

  const discardHeld = useCallback(
    async (summary: HeldBillSummary) => {
      if (!window.confirm(`Discard held bill "${summary.label ?? "Untitled"}"?`)) return;
      try {
        await billingApi.deleteHeldBill(summary.id);
        refreshHeldBills();
      } catch (e) {
        setError(errorText(e));
      }
    },
    [refreshHeldBills],
  );

  const save = useCallback(async () => {
    setSaving(true);
    setError(null);
    for (const p of pricedLines) {
      if (p.discountPaise === null) {
        setError("Fix line discount amounts before saving");
        setSaving(false);
        return;
      }
    }
    if (billDiscountPaise === null) {
      setError("Fix the bill discount amount before saving");
      setSaving(false);
      return;
    }
    try {
      const payload: Record<string, unknown> = {
        lines: pricedLines.map((p) => ({
          itemId: p.line.item.id,
          qtyMilli: p.line.qtyMilli,
          discountPaise: p.discountPaise ?? 0,
        })),
        interState,
        paymentMode: payment,
        billDiscountPaise: billDiscountPaise ?? 0,
      };
      if (selectedCustomer) {
        payload.customerId = selectedCustomer.id;
      } else if (showNewCustomer && inlineCustomer.name.trim()) {
        payload.customer = {
          name: inlineCustomer.name,
          phone: inlineCustomer.phone || null,
          gstin: inlineCustomer.gstin || null,
          stateCode: inlineCustomer.gstin ? null : inlineCustomer.stateCode || null,
          address: inlineCustomer.address || null,
        };
        payload.saveCustomer = saveCustomer;
      }
      const inv = await billingApi.createInvoice(payload);
      navigate(`/invoices/${inv.id}`, { state: { autoPrint: true } });
      clearBill();
    } catch (e) {
      setError(errorText(e));
    } finally {
      setSaving(false);
    }
  }, [
    pricedLines,
    billDiscountPaise,
    selectedCustomer,
    showNewCustomer,
    inlineCustomer,
    saveCustomer,
    interState,
    payment,
    navigate,
    clearBill,
  ]);

  saveRef.current = () => {
    if (!saving && cart.length > 0) void save();
  };

  holdRef.current = () => {
    if (!holding && cart.length > 0) void holdBill();
  };

  const summaryPanel = (
    <>
      {autoPlaceOfSupply && customerStateCode ? (
        <p className="m-0 text-sm text-muted">
          Auto: {effectiveInterState ? "Other state" : "Within state"}
          {" — "}
          {gstStateName(customerStateCode)}
        </p>
      ) : (
        <Segmented
          label="Place of supply"
          value={interState ? "inter" : "intra"}
          options={[
            { value: "intra", label: "Within state" },
            { value: "inter", label: "Other state" },
          ]}
          onChange={(v) => setInterState(v === "inter")}
        />
      )}

      <DiscountInput
        label="Bill discount"
        ariaLabel="Bill discount"
        entry={billDiscount}
        onChange={setBillDiscount}
        invalid={billDiscountPaise === null}
      />

      <dl className="m-0 grid grid-cols-[1fr_auto] gap-x-3 gap-y-2 text-sm">
        <dt className="text-muted">Gross</dt>
        <dd className="m-0 text-right font-mono">{formatPaise(totals.gross)}</dd>
        <dt className="text-muted">Discount</dt>
        <dd className="m-0 text-right font-mono">{formatPaise(totals.discount)}</dd>
        <dt className="text-muted">Taxable value</dt>
        <dd className="m-0 text-right font-mono">{formatPaise(totals.taxable)}</dd>
        {effectiveInterState ? (
          <>
            <dt className="text-muted">IGST</dt>
            <dd className="m-0 text-right font-mono">{formatPaise(totals.igst)}</dd>
          </>
        ) : (
          <>
            <dt className="text-muted">CGST</dt>
            <dd className="m-0 text-right font-mono">{formatPaise(totals.cgst)}</dd>
            <dt className="text-muted">SGST</dt>
            <dd className="m-0 text-right font-mono">{formatPaise(totals.sgst)}</dd>
          </>
        )}
        <dt className="text-muted">Round off</dt>
        <dd className="m-0 text-right font-mono">{formatPaise(totals.roundOff)}</dd>
      </dl>
      <div className="flex items-baseline justify-between gap-3 border-t border-line-soft pt-3.5">
        <span className="text-[15px] font-semibold">Total</span>
        <span className="font-mono text-3xl font-medium text-brand">{formatPaise(totals.total)}</span>
      </div>

      <Segmented
        label="Payment"
        value={payment}
        options={[
          { value: "CASH", label: "Cash" },
          { value: "UPI", label: "UPI" },
          { value: "CARD", label: "Card" },
        ]}
        onChange={(v) => setPayment(v as PaymentMode)}
      />

      <ErrorNote message={error} />
      <Button className="min-h-11" disabled={cart.length === 0 || holding} onClick={() => void holdBill()}>
        {holding ? "Holding…" : "Hold (F8)"}
      </Button>
      <Button variant="primary" className="min-h-11" disabled={cart.length === 0 || saving} onClick={save}>
        {saving ? "Saving…" : "Save & print (F10)"}
      </Button>
    </>
  );

  return (
    <div className="pb-28 lg:pb-0">
    <div className="flex w-full min-w-0 flex-col gap-5 lg:flex-row lg:items-stretch lg:gap-6">
      {showHeldPanel && (
        <HeldBillsPanel
          bills={heldBills}
          onResume={(b) => void resumeHeld(b)}
          onDiscard={(b) => void discardHeld(b)}
          onClose={() => setShowHeldPanel(false)}
        />
      )}
      <section aria-label="Bill items" className="flex min-w-0 flex-1 flex-col gap-4">
        <header className="flex flex-wrap items-center justify-between gap-3">
          <h1 className="m-0 text-2xl font-semibold">New bill</h1>
          <div className="flex flex-wrap items-center gap-2">
            <Button type="button" className="min-h-9 text-[13px]" onClick={() => setShowHeldPanel(true)}>
              Held bills ({heldBills.length})
            </Button>
            <span className="text-[13px] text-muted">Billed by: {user.name}</span>
          </div>
        </header>
        {resumeNotice && (
          <p className="m-0 rounded-lg border border-brand/30 bg-brand-soft px-3 py-2 text-sm text-brand">{resumeNotice}</p>
        )}

        <div className="rounded-xl border border-line bg-surface p-4">
          <label htmlFor="customer-search" className="mb-1.5 block text-[13px] font-medium">
            Customer <span className="text-muted">(F4)</span>
          </label>
          {selectedCustomer ? (
            <div className="flex flex-wrap items-center gap-2">
              <span className="rounded-lg bg-brand-soft px-3 py-2 text-sm">
                <span className="font-medium">{selectedCustomer.name}</span>
                {selectedCustomer.phone && <span className="text-muted"> · {selectedCustomer.phone}</span>}
                {selectedCustomer.gstin && <span className="font-mono text-[12px]"> · {selectedCustomer.gstin}</span>}
                {selectedCustomer.stateCode && (
                  <span className="text-muted"> · {gstStateName(selectedCustomer.stateCode)}</span>
                )}
              </span>
              <Button type="button" className="min-h-9 px-3 text-[13px]" onClick={() => setSelectedCustomer(null)}>
                Change
              </Button>
            </div>
          ) : showNewCustomer ? (
            <div className="flex flex-col gap-3">
              <Field label="Name" value={inlineCustomer.name} onChange={(e) => setInlineCustomer({ ...inlineCustomer, name: e.target.value })} required />
              <div className="grid grid-cols-2 gap-3">
                <Field label="Phone" value={inlineCustomer.phone} onChange={(e) => setInlineCustomer({ ...inlineCustomer, phone: e.target.value })} inputMode="tel" />
                <Field
                  label="GSTIN"
                  value={inlineCustomer.gstin}
                  onChange={(e) => {
                    const gstin = e.target.value.toUpperCase();
                    const code = stateCodeFromGstin(gstin);
                    setInlineCustomer({ ...inlineCustomer, gstin, stateCode: code ?? inlineCustomer.stateCode });
                  }}
                  maxLength={15}
                />
              </div>
              <label className="flex cursor-pointer items-center gap-2 text-sm">
                <input type="checkbox" checked={saveCustomer} onChange={(e) => setSaveCustomer(e.target.checked)} className="size-4 rounded border-[#b9c2bd]" />
                Save customer for next time
              </label>
              <div className="flex gap-2">
                <Button type="button" className="min-h-9 text-[13px]" onClick={() => { setShowNewCustomer(false); setInlineCustomer(emptyInline()); }}>
                  Cancel
                </Button>
              </div>
            </div>
          ) : (
            <div className="relative flex flex-wrap gap-2">
              <input
                id="customer-search"
                ref={customerSearchRef}
                type="search"
                autoComplete="off"
                placeholder="Search name, phone or GSTIN — leave empty for walk-in"
                value={customerQuery}
                onChange={(e) => setCustomerQuery(e.target.value)}
                className="min-h-11 min-w-0 flex-1 rounded-lg border border-[#b9c2bd] bg-surface px-3 text-base sm:text-sm"
              />
              <Button type="button" className="min-h-10" onClick={() => setShowNewCustomer(true)}>
                New customer
              </Button>
              {customerResults.length > 0 && (
                <ul className="absolute z-10 mt-11 max-h-48 w-full overflow-auto rounded-lg border border-line bg-surface p-1 shadow-lg">
                  {customerResults.map((c) => (
                    <li key={c.id}>
                      <button
                        type="button"
                        onClick={() => {
                          setSelectedCustomer(c);
                          setCustomerQuery("");
                          setCustomerResults([]);
                        }}
                        className="flex min-h-10 w-full flex-col rounded-md px-3 py-1.5 text-left text-sm hover:bg-brand-soft"
                      >
                        <span className="font-medium">{c.name}</span>
                        <span className="text-xs text-muted">
                          {[c.phone, c.gstin, c.stateCode ? gstStateName(c.stateCode) : null].filter(Boolean).join(" · ")}
                        </span>
                      </button>
                    </li>
                  ))}
                </ul>
              )}
            </div>
          )}
        </div>

        <div className="relative">
          <label htmlFor="scan" className="mb-1.5 block text-[13px] font-medium">
            Scan barcode or search item <span className="text-muted">(F2)</span>
          </label>
          <input
            id="scan"
            ref={searchRef}
            type="search"
            autoFocus
            autoComplete="off"
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={onSearchKey}
            placeholder="Name, code or barcode"
            className="min-h-12 w-full rounded-lg border border-[#b9c2bd] bg-surface px-3.5 text-base outline-none focus:border-brand focus:ring-2 focus:ring-brand/20"
          />
          {results.length > 0 && (
            <ul className="absolute z-10 mt-1 max-h-72 w-full overflow-auto rounded-lg border border-line bg-surface p-1 shadow-lg">
              {results.slice(0, 20).map((i) => (
                <li key={i.id}>
                  <button
                    type="button"
                    onClick={() => add(i)}
                    className="flex min-h-11 w-full items-center justify-between gap-3 rounded-md px-3 text-left text-sm hover:bg-brand-soft"
                  >
                    <span>
                      {i.name}{" "}
                      <span className="text-xs text-muted">
                        {i.hsn ? `HSN ${i.hsn}` : ""}
                        {i.taxInclusive ? " · incl. GST" : ""}
                      </span>
                    </span>
                    <span className="font-mono">{formatPaise(i.ratePaise)}</span>
                  </button>
                </li>
              ))}
            </ul>
          )}
        </div>

        <div className="hidden rounded-xl border border-line bg-surface md:block">
          <table className="w-full border-collapse text-sm">
            <thead>
              <tr className="text-left text-xs uppercase tracking-wider text-muted">
                <th className="px-4 py-2.5 font-medium">#</th>
                <th className="px-2 py-2.5 font-medium">Item</th>
                <th className="px-2 py-2.5 font-medium">HSN</th>
                <th className="px-2 py-2.5 text-right font-medium">Rate</th>
                <th className="px-2 py-2.5 text-center font-medium">Qty</th>
                <th className="px-2 py-2.5 font-medium">Disc.</th>
                <th className="px-2 py-2.5 text-right font-medium">GST</th>
                <th className="px-4 py-2.5 text-right font-medium">Gross</th>
              </tr>
            </thead>
            <tbody>
              {cart.length === 0 && (
                <tr className="border-t border-line-soft">
                  <td colSpan={8} className="px-4 py-6 text-center text-muted">
                    Scan or search an item to start the bill.
                  </td>
                </tr>
              )}
              {pricedLines.map((p, n) => {
                const l = p.line;
                const decimal = isDecimalUnit(l.item.unit);
                const step = decimal ? 250 : 1000;
                return (
                  <tr key={l.item.id} className="border-t border-line-soft">
                    <td className="px-4 py-2.5 text-muted">{n + 1}</td>
                    <td className="px-2 py-2.5 font-medium">
                      {l.item.name}
                      {l.item.taxInclusive && (
                        <span className="ml-1.5 rounded bg-brand-soft px-1.5 py-0.5 text-[10px] font-semibold uppercase text-brand">
                          incl. GST
                        </span>
                      )}
                    </td>
                    <td className="px-2 py-2.5 font-mono text-[13px]">{l.item.hsn ?? "—"}</td>
                    <td className="px-2 py-2.5 text-right font-mono">{formatPaise(l.item.ratePaise)}</td>
                    <td className="px-2 py-1.5">
                      <div className="flex items-center justify-center gap-1.5">
                        <button
                          type="button"
                          aria-label={`Decrease ${l.item.name}`}
                          onClick={() => bumpQty(l, -step)}
                          className="size-8 rounded-md border border-[#b9c2bd] bg-surface"
                        >
                          −
                        </button>
                        <input
                          aria-label={`Quantity of ${l.item.name}`}
                          inputMode={decimal ? "decimal" : "numeric"}
                          value={formatQty(l.qtyMilli, l.item.unit)}
                          onChange={(e) => {
                            const parsed = parseQty(e.target.value, l.item.unit);
                            if (parsed !== null) setQtyMilli(l.item.id, parsed);
                          }}
                          className="h-8 w-16 rounded-md border border-[#b9c2bd] text-center font-mono"
                        />
                        <span className="text-xs text-muted">{l.item.unit}</span>
                        <button
                          type="button"
                          aria-label={`Increase ${l.item.name}`}
                          onClick={() => bumpQty(l, step)}
                          className="size-8 rounded-md border border-[#b9c2bd] bg-surface"
                        >
                          +
                        </button>
                      </div>
                    </td>
                    <td className="px-2 py-1.5">
                      <DiscountInput
                        ariaLabel={`Discount on ${l.item.name}`}
                        entry={l.discount}
                        onChange={(d) => setLineDiscount(l.item.id, d)}
                        invalid={p.discountPaise === null}
                      />
                    </td>
                    <td className="px-2 py-2.5 text-right">{formatGstRate(l.item.gstRateBp)}</td>
                    <td className="px-4 py-2.5 text-right font-mono font-medium">{formatPaise(p.gross)}</td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
        <div className="flex flex-col gap-2 md:hidden">
          {cart.length === 0 && (
            <p className="rounded-xl border border-line bg-surface px-4 py-6 text-center text-sm text-muted">
              Scan or search an item to start the bill.
            </p>
          )}
          {pricedLines.map((p, n) => {
            const l = p.line;
            const decimal = isDecimalUnit(l.item.unit);
            const step = decimal ? 250 : 1000;
            return (
              <div key={l.item.id} className="rounded-xl border border-line bg-surface p-4 text-sm">
                <div className="flex items-start justify-between gap-2">
                  <div className="min-w-0 font-medium">
                    {n + 1}. {l.item.name}
                  </div>
                  <span className="shrink-0 font-mono font-semibold">{formatPaise(p.gross)}</span>
                </div>
                <div className="mt-2 flex flex-wrap items-center gap-2 text-xs text-muted">
                  <span>{l.item.hsn ?? "—"}</span>
                  <span>{formatPaise(l.item.ratePaise)}</span>
                  <span>{formatGstRate(l.item.gstRateBp)}</span>
                </div>
                <div className="mt-3 flex flex-wrap items-center gap-2">
                  <button type="button" aria-label="Decrease qty" onClick={() => bumpQty(l, -step)} className="flex min-h-11 min-w-11 items-center justify-center rounded-lg border border-line">
                    −
                  </button>
                  <input
                    aria-label={`Quantity of ${l.item.name}`}
                    inputMode={decimal ? "decimal" : "numeric"}
                    value={formatQty(l.qtyMilli, l.item.unit)}
                    onChange={(e) => {
                      const parsed = parseQty(e.target.value, l.item.unit);
                      if (parsed !== null) setQtyMilli(l.item.id, parsed);
                    }}
                    className="min-h-11 w-20 rounded-lg border border-line text-center font-mono text-base"
                  />
                  <span className="text-xs">{l.item.unit}</span>
                  <button type="button" aria-label="Increase qty" onClick={() => bumpQty(l, step)} className="flex min-h-11 min-w-11 items-center justify-center rounded-lg border border-line">
                    +
                  </button>
                </div>
                <div className="mt-2">
                  <DiscountInput
                    ariaLabel={`Discount on ${l.item.name}`}
                    entry={l.discount}
                    onChange={(d) => setLineDiscount(l.item.id, d)}
                    invalid={p.discountPaise === null}
                  />
                </div>
              </div>
            );
          })}
        </div>
        <p className="m-0 text-xs text-muted">Saved invoices cannot be edited. Corrections will use credit notes.</p>
      </section>

      <aside
        aria-label="Bill summary"
        className="hidden w-full shrink-0 flex-col gap-4 rounded-xl border border-line bg-surface p-5 lg:flex lg:w-[360px] lg:sticky lg:top-6 lg:self-start"
      >
        {summaryPanel}
      </aside>
    </div>

      <div className="fixed bottom-16 left-0 right-0 z-20 flex items-center gap-3 border-t border-line bg-surface px-4 py-3 shadow-lg sm:bottom-0 lg:hidden">
        <div className="min-w-0 flex-1">
          <div className="text-xs text-muted">Total</div>
          <div className="font-mono text-xl font-semibold text-brand">{formatPaise(totals.total)}</div>
        </div>
        <Button variant="primary" className="min-h-11 shrink-0 px-4" disabled={cart.length === 0} onClick={() => setSummaryOpen(true)}>
          Save & print
        </Button>
      </div>

      {summaryOpen && (
        <div className="fixed inset-0 z-30 flex flex-col bg-black/40 lg:hidden">
          <button type="button" className="min-h-11 flex-1" aria-label="Close summary" onClick={() => setSummaryOpen(false)} />
          <div className="max-h-[85vh] overflow-y-auto rounded-t-2xl border border-line bg-surface p-5">
            <div className="mb-4 flex items-center justify-between gap-2">
              <h2 className="m-0 text-lg font-semibold">Bill summary</h2>
              <Button type="button" className="min-h-11" onClick={() => setSummaryOpen(false)}>
                Close
              </Button>
            </div>
            <div className="flex flex-col gap-4">{summaryPanel}</div>
          </div>
        </div>
      )}
    </div>
  );
}

function DiscountInput({
  label,
  ariaLabel,
  entry,
  onChange,
  invalid,
}: {
  label?: string;
  ariaLabel: string;
  entry: DiscountEntry;
  onChange: (e: DiscountEntry) => void;
  invalid?: boolean;
}) {
  return (
    <div className="flex flex-col gap-1">
      {label && <span className="text-[13px] font-medium">{label}</span>}
      <div className="flex items-center gap-1">
        <input
          aria-label={ariaLabel}
          inputMode="decimal"
          value={entry.value}
          onChange={(e) => onChange({ ...entry, value: e.target.value })}
          placeholder="0"
          className={`h-8 min-w-0 flex-1 rounded-md border px-2 text-right font-mono text-[13px] ${
            invalid ? "border-danger" : "border-[#b9c2bd]"
          }`}
        />
        <ModeToggle mode={entry.mode} onChange={(mode) => onChange({ ...entry, mode })} />
      </div>
    </div>
  );
}

function ModeToggle({ mode, onChange }: { mode: DiscountMode; onChange: (m: DiscountMode) => void }) {
  return (
    <div className="flex shrink-0 overflow-hidden rounded-md border border-[#b9c2bd] text-[11px] font-semibold">
      <button
        type="button"
        aria-pressed={mode === "rupee"}
        onClick={() => onChange("rupee")}
        className={`px-2 py-1 ${mode === "rupee" ? "bg-brand text-white" : "bg-surface"}`}
      >
        ₹
      </button>
      <button
        type="button"
        aria-pressed={mode === "percent"}
        onClick={() => onChange("percent")}
        className={`px-2 py-1 ${mode === "percent" ? "bg-brand text-white" : "bg-surface"}`}
      >
        %
      </button>
    </div>
  );
}

function Segmented({ label, value, options, onChange }: {
  label: string;
  value: string;
  options: { value: string; label: string }[];
  onChange: (v: string) => void;
}) {
  return (
    <fieldset className="m-0 flex flex-col gap-2 border-0 p-0">
      <legend className="mb-2 text-[13px] font-medium">{label}</legend>
      <div className="grid gap-2" style={{ gridTemplateColumns: `repeat(${options.length}, minmax(0, 1fr))` }}>
        {options.map((o) => (
          <button
            key={o.value}
            type="button"
            aria-pressed={value === o.value}
            onClick={() => onChange(o.value)}
            className={`min-h-10 rounded-lg text-[13px] font-semibold ${
              value === o.value ? "border border-brand bg-brand text-white" : "border border-[#b9c2bd] bg-surface"
            }`}
          >
            {o.label}
          </button>
        ))}
      </div>
    </fieldset>
  );
}
