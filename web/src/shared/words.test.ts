import { strict as assert } from "node:assert";
import test from "node:test";
import { amountInWords } from "./words.ts";

test("amountInWords crore example", () => {
  assert.equal(
    amountInWords(1005002550),
    "One Crore Fifty Thousand Twenty Five Rupees and Fifty Paise Only",
  );
});

test("amountInWords one rupee", () => {
  assert.equal(amountInWords(100), "One Rupee Only");
});

test("amountInWords paise only", () => {
  assert.equal(amountInWords(50), "Zero Rupees and Fifty Paise Only");
});

test("amountInWords zero", () => {
  assert.equal(amountInWords(0), "Zero Rupees Only");
});
