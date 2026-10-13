// Live preview while billing. Mirrors server GstCalculator; the server recalculates
// on save and its numbers are what get stored and printed.

export interface PreviewLine {
  ratePaise: number;
  qtyMilli: number;
  gstRateBp: number;
  taxInclusive: boolean;
  discountPaise?: number;
}

export interface PreviewTotals {
  gross: number;
  discount: number;
  taxable: number;
  cgst: number;
  sgst: number;
  igst: number;
  roundOff: number;
  total: number;
}

export const roundHalfUp = (numerator: number, denominator: number) =>
  Math.floor((numerator * 2 + denominator) / (2 * denominator));

export function lineGross(ratePaise: number, qtyMilli: number): number {
  return roundHalfUp(ratePaise * qtyMilli, 1000);
}

export type DiscountMode = "rupee" | "percent";

/** Convert UI discount input to paise (% uses roundHalfUp(gross × pct / 100)). */
export function discountInputToPaise(grossPaise: number, input: string, mode: DiscountMode): number | null {
  const t = input.trim();
  if (!t) return 0;
  if (mode === "rupee") {
    if (!/^\d+(\.\d{1,2})?$/.test(t)) return null;
    const [r, p = ""] = t.split(".");
    return Number(r) * 100 + Number(p.padEnd(2, "0"));
  }
  if (!/^\d+(\.\d{1,2})?$/.test(t)) return null;
  const pct = Number(t);
  if (pct < 0) return null;
  return roundHalfUp(grossPaise * pct, 100);
}

const ceilDiv = (amount: number, divisor: number) => Math.floor((amount + divisor - 1) / divisor);

function allocateBillDiscount(nets: number[], billDiscountPaise: number): number[] {
  const n = nets.length;
  const alloc = new Array<number>(n).fill(0);
  if (billDiscountPaise === 0 || n === 0) return alloc;

  const totalNet = nets.reduce((a, b) => a + b, 0);
  const remainders: { index: number; fraction: number }[] = [];
  let assigned = 0;

  for (let i = 0; i < n; i++) {
    const exact = (billDiscountPaise * nets[i]) / totalNet;
    const floor = Math.floor(exact);
    alloc[i] = floor;
    assigned += floor;
    remainders.push({ index: i, fraction: exact - floor });
  }

  const leftover = billDiscountPaise - assigned;
  remainders.sort((a, b) => b.fraction - a.fraction || a.index - b.index);
  for (let k = 0; k < leftover; k++) {
    alloc[remainders[k].index]++;
  }
  return alloc;
}

export function previewTotals(
  lines: PreviewLine[],
  interState: boolean,
  billDiscountPaise = 0,
): PreviewTotals {
  const n = lines.length;
  const grossArr: number[] = [];
  const netArr: number[] = [];
  const lineDiscArr: number[] = [];

  for (const l of lines) {
    const disc = l.discountPaise ?? 0;
    const gross = roundHalfUp(l.ratePaise * l.qtyMilli, 1000);
    grossArr.push(gross);
    lineDiscArr.push(disc);
    netArr.push(gross - disc);
  }

  const billAlloc = allocateBillDiscount(netArr, billDiscountPaise);

  let gross = 0;
  let discount = 0;
  let taxable = 0;
  let cgst = 0;
  let sgst = 0;
  let igst = 0;
  let exactTotal = 0;

  for (let i = 0; i < n; i++) {
    const l = lines[i];
    const g = grossArr[i];
    const d = lineDiscArr[i] + billAlloc[i];
    const net = g - d;

    let lineTaxable: number;
    let lineCgst = 0;
    let lineSgst = 0;
    let lineIgst = 0;
    let lineTotal: number;

    if (l.taxInclusive) {
      lineTaxable = roundHalfUp(net * 10_000, 10_000 + l.gstRateBp);
      const tax = net - lineTaxable;
      if (interState) lineIgst = tax;
      else {
        lineCgst = ceilDiv(tax, 2);
        lineSgst = tax - lineCgst;
      }
      lineTotal = net;
    } else {
      lineTaxable = net;
      if (interState) lineIgst = roundHalfUp(lineTaxable * l.gstRateBp, 10_000);
      else {
        lineCgst = roundHalfUp(lineTaxable * l.gstRateBp, 20_000);
        lineSgst = lineCgst;
      }
      lineTotal = lineTaxable + lineCgst + lineSgst + lineIgst;
    }

    gross += g;
    discount += d;
    taxable += lineTaxable;
    cgst += lineCgst;
    sgst += lineSgst;
    igst += lineIgst;
    exactTotal += lineTotal;
  }

  const total = roundHalfUp(exactTotal, 100) * 100;
  return { gross, discount, taxable, cgst, sgst, igst, roundOff: total - exactTotal, total };
}
