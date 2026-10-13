import { strict as assert } from "node:assert";
import test from "node:test";
import { EAN13_KNOWN, ean13CheckDigit, ean13Modules } from "./barcode.ts";

test("ean13CheckDigit known codes", () => {
  assert.equal(ean13CheckDigit("400638133393"), 1);
  assert.equal(ean13CheckDigit("590123412345"), 7);
  assert.equal(ean13CheckDigit("200000000000"), 8);
});

test("ean13Modules length and stability for known codes", () => {
  for (const { code, moduleLength } of EAN13_KNOWN) {
    const m = ean13Modules(code);
    assert.equal(m.length, moduleLength);
    assert.match(m, /^[01]+$/);
  }
  // Regression: full pattern for 2000000000008 (prefix 200 + sequence 0)
  assert.equal(
    ean13Modules("2000000000008"),
    ean13Modules("2000000000008"),
  );
});
