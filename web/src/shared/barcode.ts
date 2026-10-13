/** EAN-13 bar patterns (7 modules per digit). No external library. */

const L: readonly string[] = [
  "0001101",
  "0011001",
  "0010011",
  "0111101",
  "0100011",
  "0110001",
  "0101111",
  "0111011",
  "0110111",
  "0001011",
];

const G: readonly string[] = [
  "0100111",
  "0110011",
  "0011011",
  "0100001",
  "0011101",
  "0111001",
  "0000101",
  "0010001",
  "0001001",
  "0010111",
];

const R: readonly string[] = [
  "1110010",
  "1100110",
  "1101100",
  "1000010",
  "1011100",
  "1001110",
  "1010000",
  "1000100",
  "1001000",
  "1110100",
];

/** Parity (L/G) for digits 2–7 from first digit (GS1). */
const PARITY: Record<string, string> = {
  "0": "LLLLLL",
  "1": "LLGLGG",
  "2": "LLGGLG",
  "3": "LLGGGL",
  "4": "LGLLGG",
  "5": "LGGLLG",
  "6": "LGGGLL",
  "7": "LGLGLG",
  "8": "LGLGGL",
  "9": "LGGLGL",
};

const LEFT_GUARD = "101";
const CENTER_GUARD = "01010";
const RIGHT_GUARD = "101";

export function ean13CheckDigit(twelveDigits: string): number {
  if (!/^\d{12}$/.test(twelveDigits)) throw new Error("EAN-13 needs 12 digits");
  let sum = 0;
  for (let i = 0; i < 12; i++) {
    const d = twelveDigits.charCodeAt(i) - 48;
    sum += i % 2 === 0 ? d : d * 3;
  }
  return (10 - (sum % 10)) % 10;
}

/** Module string: '1' = black bar, '0' = gap. Includes guards; 95 modules. */
export function ean13Modules(code13: string): string {
  if (!/^\d{13}$/.test(code13)) throw new Error("EAN-13 must be 13 digits");
  const body = code13.slice(0, 12);
  const check = code13.slice(12);
  if (ean13CheckDigit(body) !== Number(check)) throw new Error("Invalid EAN-13 check digit");

  const first = code13[0];
  const parity = PARITY[first];
  if (!parity) throw new Error("Invalid first digit");

  let modules = LEFT_GUARD;
  for (let i = 0; i < 6; i++) {
    const digit = Number(code13[i + 1]);
    const enc = parity[i] === "L" ? L[digit] : G[digit];
    modules += enc;
  }
  modules += CENTER_GUARD;
  for (let i = 0; i < 6; i++) {
    const digit = Number(code13[i + 7]);
    modules += R[digit];
  }
  modules += RIGHT_GUARD;
  return modules;
}

export interface Ean13SvgOptions {
  /** Total width in user units (default 100). */
  width?: number;
  /** Bar height (default 40). */
  barHeight?: number;
  /** Quiet zone in module widths (default 11). */
  quietModules?: number;
}

/** SVG markup for an EAN-13 symbol (bars only; digits rendered by caller). */
export function ean13Svg(code13: string, opts: Ean13SvgOptions = {}): string {
  const width = opts.width ?? 100;
  const barHeight = opts.barHeight ?? 40;
  const quiet = opts.quietModules ?? 11;
  const modules = ean13Modules(code13);
  const totalModules = modules.length + quiet * 2;
  const moduleW = width / totalModules;
  const x0 = quiet * moduleW;
  let x = x0;
  const rects: string[] = [];
  for (const ch of modules) {
    if (ch === "1") {
      rects.push(`<rect x="${x.toFixed(4)}" y="0" width="${moduleW.toFixed(4)}" height="${barHeight}"/>`);
    }
    x += moduleW;
  }
  return `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 ${width} ${barHeight}" width="${width}" height="${barHeight}"><g fill="#000">${rects.join("")}</g></svg>`;
}

/** Golden module counts for known codes (see barcode.test.ts). */
export const EAN13_KNOWN: readonly { code: string; moduleLength: number }[] = [
  { code: "4006381333931", moduleLength: 95 },
  { code: "5901234123457", moduleLength: 95 },
  { code: "2000000000008", moduleLength: 95 },
];
