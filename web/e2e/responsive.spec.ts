import { spawn, type ChildProcessWithoutNullStreams } from "node:child_process";
import fs from "node:fs";
import net from "node:net";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { expect, test, type Page } from "@playwright/test";

const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const repoRoot = path.resolve(webRoot, "..");
const jarPath = path.join(repoRoot, "dist-server", "server.jar");
const shotsDir = path.join(webRoot, "e2e", "screenshots");
const javaHome = process.env.JAVA_HOME;
const javaBin = javaHome ? path.join(javaHome, "bin", "java") : "java";

const viewports = [
  { name: "390x844", width: 390, height: 844 },
  { name: "768x1024", width: 768, height: 1024 },
  { name: "1280x800", width: 1280, height: 800 },
  { name: "1920x1080", width: 1920, height: 1080 },
] as const;

type Viewport = (typeof viewports)[number];

async function freePort(): Promise<number> {
  return new Promise((resolve, reject) => {
    const s = net.createServer();
    s.listen(0, "127.0.0.1", () => {
      const addr = s.address();
      if (!addr || typeof addr === "string") {
        reject(new Error("no port"));
        return;
      }
      s.close(() => resolve(addr.port));
    });
    s.on("error", reject);
  });
}

async function waitForHealth(url: string, timeoutMs = 60_000) {
  const start = Date.now();
  while (Date.now() - start < timeoutMs) {
    try {
      const res = await fetch(url);
      if (res.ok) return;
    } catch {
      /* retry */
    }
    await new Promise((r) => setTimeout(r, 300));
  }
  throw new Error(`Server not healthy at ${url}`);
}

async function setupShop(adminPort: number) {
  const res = await fetch(`http://127.0.0.1:${adminPort}/api/setup`, {
    method: "POST",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({
      shopName: "Responsive Shop",
      gstin: "",
      stateCode: "32",
      adminName: "Owner",
      username: "owner",
      password: "secret1",
    }),
  });
  expect(res.ok).toBeTruthy();
}

async function assertNoHorizontalScroll(page: Page) {
  const overflow = await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth);
  expect(overflow, "page should not scroll horizontally").toBe(false);
}

function firstRowCardCount(grid: import("@playwright/test").Locator) {
  return grid.locator(":scope > *").evaluateAll((els) => {
    if (els.length === 0) return 0;
    const tops = els.map((e) => e.getBoundingClientRect().top);
    const t0 = tops[0]!;
    return tops.filter((t) => Math.abs(t - t0) < 3).length;
  });
}

async function assertStatGridAndTableLayout(page: Page, viewportWidth: number) {
  if (viewportWidth === 390) {
    const grids = page.getByTestId("stat-grid");
    const count = await grids.count();
    for (let i = 0; i < count; i++) {
      const grid = grids.nth(i);
      const cards = await grid.locator(":scope > *").count();
      if (cards >= 2) {
        const row = await firstRowCardCount(grid);
        expect(row, "StatGrid should show 2 cards on the first row at 390px").toBeGreaterThanOrEqual(2);
      }
    }
  }

  if (viewportWidth >= 1280) {
    const mainWidth = await page.locator("main").evaluate((el) => el.clientWidth);
    expect(mainWidth).toBeGreaterThan(0);

    const grids = page.getByTestId("stat-grid");
    const gridCount = await grids.count();
    for (let i = 0; i < gridCount; i++) {
      const grid = grids.nth(i);
      const gridWidth = await grid.evaluate((el) => el.clientWidth);
      expect(gridWidth, "StatGrid should use most of the main column").toBeGreaterThanOrEqual(mainWidth * 0.9);
      const cards = await grid.locator(":scope > *").count();
      if (cards >= 3) {
        const row = await firstRowCardCount(grid);
        expect(row, "StatGrid should place at least 3 cards on the first row at desktop widths").toBeGreaterThanOrEqual(3);
      }
    }

    const tables = page.getByTestId("data-table");
    const tableCount = await tables.count();
    for (let i = 0; i < tableCount; i++) {
      const table = tables.nth(i);
      const { tableWidth, sectionWidth } = await table.evaluate((el) => {
        const parent = el.parentElement;
        const sectionWidth = parent && parent.clientWidth > 0 ? parent.clientWidth : el.clientWidth;
        return { tableWidth: el.clientWidth, sectionWidth };
      });
      expect(tableWidth, "DataTable should use most of its section width").toBeGreaterThanOrEqual(sectionWidth * 0.9);
    }
  }
}

async function loginBilling(page: Page, billingPort: number) {
  await page.goto(`http://127.0.0.1:${billingPort}/`);
  await page.getByLabel("Username").fill("owner");
  await page.getByLabel("Password or PIN").fill("secret1");
  await page.getByRole("button", { name: /sign in/i }).click();
  await expect(page.getByRole("heading", { level: 1, name: "New bill" })).toBeVisible();
}

async function loginAdmin(page: Page, adminPort: number) {
  await page.goto(`http://127.0.0.1:${adminPort}/admin.html#/`);
  await page.getByLabel("Username").fill("owner");
  await page.getByLabel("Password or PIN").fill("secret1");
  await page.getByRole("button", { name: /sign in/i }).click();
  await expect(page.getByRole("heading", { level: 1, name: "Dashboard" })).toBeVisible();
}

async function checkRoute(
  page: Page,
  slug: string,
  vp: Viewport,
  pathHash: string,
  heading: string | RegExp,
) {
  await page.goto(pathHash);
  await expect(page.getByRole("heading", { level: 1, name: heading })).toBeVisible();
  await page.waitForTimeout(300);
  await page.screenshot({ path: path.join(shotsDir, `${slug}-${vp.name}.png`), fullPage: true });
  await assertNoHorizontalScroll(page);
  await assertStatGridAndTableLayout(page, vp.width);
}

test.describe("responsive layouts", () => {
  let server: ChildProcessWithoutNullStreams;
  let dataDir: string;
  let adminPort: number;
  let billingPort: number;
  let invoiceId: string;
  let creditNoteId: string;
  let purchaseId: string;
  const itemName = "Test item";

  test.beforeAll(async () => {
    if (!fs.existsSync(jarPath)) {
      throw new Error(`Missing ${jarPath}. Run ./scripts/build-server.sh first.`);
    }
    fs.mkdirSync(shotsDir, { recursive: true });
    dataDir = fs.mkdtempSync(path.join(os.tmpdir(), "openbill-responsive-"));
    fs.mkdirSync(path.join(dataDir, "logs"), { recursive: true });
    adminPort = await freePort();
    billingPort = await freePort();

    server = spawn(
      javaBin,
      [
        "-jar",
        jarPath,
        `--app.admin-port=${adminPort}`,
        `--app.billing-port=${billingPort}`,
        `--app.public-port=${await freePort()}`,
      ],
      { env: { ...process.env, APP_DATA_DIR: dataDir }, stdio: "pipe" },
    );

    await waitForHealth(`http://127.0.0.1:${billingPort}/api/health`);
    await setupShop(adminPort);

    const adminLogin = await fetch(`http://127.0.0.1:${adminPort}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: "owner", password: "secret1" }),
    });
    expect(adminLogin.ok).toBeTruthy();
    const { token: adminToken } = (await adminLogin.json()) as { token: string };

    const billingLogin = await fetch(`http://127.0.0.1:${billingPort}/api/auth/login`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ username: "owner", password: "secret1" }),
    });
    expect(billingLogin.ok).toBeTruthy();
    const { token: billingToken } = (await billingLogin.json()) as { token: string };

    const authAdmin = { "Content-Type": "application/json", Authorization: `Bearer ${adminToken}` };
    const authBilling = { "Content-Type": "application/json", Authorization: `Bearer ${billingToken}` };

    const itemRes = await fetch(`http://127.0.0.1:${adminPort}/api/items`, {
      method: "POST",
      headers: authAdmin,
      body: JSON.stringify({ name: itemName, ratePaise: 10000, gstRateBp: 1200, stockMilli: 10000 }),
    });
    expect(itemRes.ok).toBeTruthy();
    const item = (await itemRes.json()) as { id: string };

    const invRes = await fetch(`http://127.0.0.1:${billingPort}/api/invoices`, {
      method: "POST",
      headers: authBilling,
      body: JSON.stringify({
        lines: [{ itemId: item.id, qtyMilli: 1000 }],
        interState: false,
        paymentMode: "CASH",
      }),
    });
    expect(invRes.ok).toBeTruthy();
    const inv = (await invRes.json()) as { id: string };
    invoiceId = inv.id;

    const invDetail = await fetch(`http://127.0.0.1:${billingPort}/api/invoices/${invoiceId}`, {
      headers: { Authorization: `Bearer ${billingToken}` },
    });
    expect(invDetail.ok).toBeTruthy();
    const invBody = (await invDetail.json()) as { lines: { id: string }[] };
    const lineId = invBody.lines[0]?.id;
    expect(lineId).toBeTruthy();

    const cnRes = await fetch(`http://127.0.0.1:${billingPort}/api/credit-notes`, {
      method: "POST",
      headers: authBilling,
      body: JSON.stringify({
        invoiceId,
        reason: "E2E partial return",
        refundMode: "CASH",
        lines: [{ invoiceLineId: lineId, qtyMilli: 500 }],
      }),
    });
    expect(cnRes.ok).toBeTruthy();
    const cn = (await cnRes.json()) as { id: string };
    creditNoteId = cn.id;

    const supplierRes = await fetch(`http://127.0.0.1:${billingPort}/api/suppliers`, {
      method: "POST",
      headers: authBilling,
      body: JSON.stringify({ name: "E2E Supplier", gstin: "32AAAAA0000A1Z5" }),
    });
    expect(supplierRes.ok).toBeTruthy();
    const supplier = (await supplierRes.json()) as { id: string };
    const today = new Date().toISOString().slice(0, 10);

    const purchaseRes = await fetch(`http://127.0.0.1:${billingPort}/api/purchases`, {
      method: "POST",
      headers: authBilling,
      body: JSON.stringify({
        supplierId: supplier.id,
        billNo: "PB-E2E-001",
        billDate: today,
        lines: [{ itemId: item.id, qtyMilli: 1000, costPaise: 5000, gstRateBp: 1200 }],
      }),
    });
    expect(purchaseRes.ok).toBeTruthy();
    const purchase = (await purchaseRes.json()) as { id: string };
    purchaseId = purchase.id;
  });

  test.afterAll(async () => {
    if (server && !server.killed) {
      server.kill("SIGTERM");
      await new Promise<void>((resolve) => server.on("exit", () => resolve()));
    }
  });

  type RouteDef = {
    slug: string;
    path: (ids: { invoiceId: string; creditNoteId: string; purchaseId: string }) => string;
    heading: string | RegExp;
  };

  const billingRouteDefs: RouteDef[] = [
    { slug: "new-bill", path: () => "#/", heading: "New bill" },
    { slug: "invoices", path: () => "#/invoices", heading: "Invoices" },
    { slug: "invoice-detail", path: (ids) => `#/invoices/${ids.invoiceId}`, heading: /INV\//i },
    { slug: "create-credit-note", path: (ids) => `#/invoices/${ids.invoiceId}/credit-note`, heading: "Create credit note" },
    { slug: "credit-notes", path: () => "#/credit-notes", heading: "Credit notes" },
    { slug: "credit-note-detail", path: (ids) => `#/credit-notes/${ids.creditNoteId}`, heading: /CN\//i },
    { slug: "customers", path: () => "#/customers", heading: "Customers" },
    { slug: "suppliers", path: () => "#/suppliers", heading: "Suppliers" },
    { slug: "items", path: () => "#/items", heading: "Items & stock" },
    { slug: "purchases", path: () => "#/purchases", heading: "Purchases" },
    { slug: "new-purchase", path: () => "#/purchases/new", heading: "New purchase" },
    { slug: "purchase-detail", path: (ids) => `#/purchases/${ids.purchaseId}`, heading: "PB-E2E-001" },
    { slug: "reports-day-end", path: () => "#/reports", heading: "Reports" },
  ];

  const adminRoutes: { slug: string; path: string; heading: string | RegExp }[] = [
    { slug: "admin-dashboard", path: "#/", heading: "Dashboard" },
    { slug: "admin-access", path: "#/access", heading: "Server & access" },
    { slug: "admin-users", path: "#/users", heading: "Users & logins" },
    { slug: "admin-activity", path: "#/activity", heading: "Activity log" },
    { slug: "admin-backup", path: "#/backup", heading: "Backup" },
    { slug: "admin-printers", path: "#/printers", heading: "Receipt printers" },
    { slug: "admin-settings", path: "#/settings", heading: "Shop & GST settings" },
  ];

  for (const vp of viewports) {
    for (const routeDef of billingRouteDefs) {
      test(`billing ${routeDef.slug} @ ${vp.name}`, async ({ browser }) => {
        const ids = { invoiceId, creditNoteId, purchaseId };
        const context = await browser.newContext({
          viewport: { width: vp.width, height: vp.height },
          baseURL: `http://127.0.0.1:${billingPort}`,
        });
        const page = await context.newPage();
        await loginBilling(page, billingPort);
        await checkRoute(page, routeDef.slug, vp, routeDef.path(ids), routeDef.heading);
        await context.close();
      });
    }

    test(`billing label-print @ ${vp.name}`, async ({ browser }) => {
      const context = await browser.newContext({
        viewport: { width: vp.width, height: vp.height },
        baseURL: `http://127.0.0.1:${billingPort}`,
      });
      const page = await context.newPage();
      await loginBilling(page, billingPort);
      await page.goto("#/items");
      await expect(page.getByRole("heading", { level: 1, name: "Items & stock" })).toBeVisible();
      await page.getByRole("checkbox", { name: `Select ${itemName}` }).check();
      await page.getByRole("button", { name: /Print labels/i }).click();
      await expect(page.getByRole("heading", { level: 1, name: "Print labels" })).toBeVisible();
      await page.waitForTimeout(300);
      await page.screenshot({ path: path.join(shotsDir, `label-print-${vp.name}.png`), fullPage: true });
      await assertNoHorizontalScroll(page);
      await assertStatGridAndTableLayout(page, vp.width);
      await context.close();
    });

    test(`billing held-bills-panel @ ${vp.name}`, async ({ browser }) => {
      const context = await browser.newContext({
        viewport: { width: vp.width, height: vp.height },
        baseURL: `http://127.0.0.1:${billingPort}`,
      });
      const page = await context.newPage();
      await loginBilling(page, billingPort);
      await page.goto("#/");
      await page.getByRole("button", { name: /Held bills/i }).click();
      await expect(page.getByRole("dialog", { name: "Held bills" })).toBeVisible();
      await page.waitForTimeout(300);
      await page.screenshot({ path: path.join(shotsDir, `held-bills-panel-${vp.name}.png`), fullPage: true });
      await assertNoHorizontalScroll(page);
      await context.close();
    });

    for (const route of adminRoutes) {
      test(`admin ${route.slug} @ ${vp.name}`, async ({ browser }) => {
        const context = await browser.newContext({
          viewport: { width: vp.width, height: vp.height },
          baseURL: `http://127.0.0.1:${adminPort}`,
        });
        const page = await context.newPage();
        await loginAdmin(page, adminPort);
        await checkRoute(page, route.slug, vp, `/admin.html${route.path}`, route.heading);
        await context.close();
      });
    }
  }
});
