// Indian English amount in words (thousand, lakh, crore).
// Examples:
//   amountInWords(1005002550) → "One Crore Fifty Thousand Twenty Five Rupees and Fifty Paise Only" (₹1,00,50,025.50)
//   amountInWords(100) → "One Rupee Only"
//   amountInWords(50) → "Zero Rupees and Fifty Paise Only"

const ONES = [
  "",
  "One",
  "Two",
  "Three",
  "Four",
  "Five",
  "Six",
  "Seven",
  "Eight",
  "Nine",
  "Ten",
  "Eleven",
  "Twelve",
  "Thirteen",
  "Fourteen",
  "Fifteen",
  "Sixteen",
  "Seventeen",
  "Eighteen",
  "Nineteen",
];

const TENS = ["", "", "Twenty", "Thirty", "Forty", "Fifty", "Sixty", "Seventy", "Eighty", "Ninety"];

function under1000(n: number): string {
  if (n === 0) return "";
  let s = "";
  const h = Math.floor(n / 100);
  const r = n % 100;
  if (h > 0) {
    s += ONES[h] + " Hundred";
    if (r > 0) s += " ";
  }
  if (r > 0) {
    if (r < 20) s += ONES[r];
    else s += TENS[Math.floor(r / 10)] + (r % 10 ? " " + ONES[r % 10] : "");
  }
  return s;
}

function indianChunk(n: number, label: string): string {
  if (n === 0) return "";
  return under1000(n) + " " + label;
}

function rupeesWords(rupees: number): string {
  if (rupees === 0) return "";
  let n = rupees;
  const parts: string[] = [];
  const crore = Math.floor(n / 10_000_000);
  n %= 10_000_000;
  const lakh = Math.floor(n / 100_000);
  n %= 100_000;
  const thousand = Math.floor(n / 1000);
  n %= 1000;
  const c = indianChunk(crore, "Crore");
  const l = indianChunk(lakh, "Lakh");
  const t = indianChunk(thousand, "Thousand");
  const rest = under1000(n);
  if (c) parts.push(c.trim());
  if (l) parts.push(l.trim());
  if (t) parts.push(t.trim());
  if (rest) parts.push(rest);
  return parts.join(" ");
}

export function amountInWords(paise: number): string {
  if (!Number.isFinite(paise) || paise < 0) return "Zero Rupees Only";
  const rupees = Math.floor(paise / 100);
  const p = paise % 100;
  const rWords = rupeesWords(rupees);
  const pWords = p > 0 ? under1000(p) : "";
  if (rupees === 0 && p === 0) return "Zero Rupees Only";
  if (rupees === 0) return `Zero Rupees and ${pWords} Paise Only`;
  const rupeeLabel = rupees === 1 ? "Rupee" : "Rupees";
  if (p === 0) return `${rWords} ${rupeeLabel} Only`;
  return `${rWords} ${rupeeLabel} and ${pWords} Paise Only`;
}
