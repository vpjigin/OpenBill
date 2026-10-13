import { defineConfig, devices } from "@playwright/test";

export default defineConfig({
  testDir: "./e2e",
  timeout: 120_000,
  expect: { timeout: 15_000 },
  fullyParallel: false,
  workers: 1,
  use: {
    ...devices["Desktop Chrome"],
    channel: "chrome",
    baseURL: process.env.E2E_BILLING_URL ?? "http://127.0.0.1:8080",
  },
});
