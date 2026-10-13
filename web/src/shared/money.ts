// All money moves as integer paise. Format only at the edge.

const inr = new Intl.NumberFormat("en-IN", {
  style: "currency",
  currency: "INR",
  minimumFractionDigits: 2,
});

export function formatPaise(paise: number): string {
  return inr.format(paise / 100);
}

/** "12.5" -> 1250. Returns null for invalid input. */
export function rupeesToPaise(input: string): number | null {
  const t = input.trim();
  if (!/^\d+(\.\d{1,2})?$/.test(t)) return null;
  const [r, p = ""] = t.split(".");
  return Number(r) * 100 + Number(p.padEnd(2, "0"));
}

export function paiseToRupeesInput(paise: number): string {
  return (paise / 100).toFixed(2);
}

/** 1200 -> "12%" */
export function formatGstRate(bp: number): string {
  return `${bp / 100}%`;
}

const DECIMAL_UNITS = new Set(["KG", "G", "LTR", "ML", "MTR"]);

export function isDecimalUnit(unit: string): boolean {
  return DECIMAL_UNITS.has(unit.trim().toUpperCase());
}

/** Display quantity from milli-units (max 3 decimal places, no trailing zeros). */
export function formatQty(qtyMilli: number, unit: string): string {
  if (!isDecimalUnit(unit)) {
    return String(Math.round(qtyMilli / 1000));
  }
  const s = (qtyMilli / 1000).toFixed(3);
  return s.replace(/\.?0+$/, "");
}

/**
 * Parse user-entered quantity to milli-units. Whole-unit items must be integers.
 * Returns null when invalid.
 */
export function parseQty(text: string, unit: string): number | null {
  const t = text.trim();
  if (!t) return null;
  if (isDecimalUnit(unit)) {
    if (!/^\d+(\.\d{1,3})?$/.test(t)) return null;
    const [whole, frac = ""] = t.split(".");
    const milli = Number(whole) * 1000 + Number(frac.padEnd(3, "0").slice(0, 3));
    return milli > 0 ? milli : null;
  }
  if (!/^\d+$/.test(t)) return null;
  const n = Number(t);
  return n > 0 ? n * 1000 : null;
}

/** Stock / qty input from milli-units. */
export function qtyMilliToInput(qtyMilli: number, unit: string): string {
  return formatQty(qtyMilli, unit);
}

/** Stock field → milli-units. */
export function inputToQtyMilli(text: string, unit: string): number | null {
  return parseQty(text, unit);
}
