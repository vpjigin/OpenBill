import { spawn, type ChildProcessWithoutNullStreams } from "node:child_process";
import fs from "node:fs";
import net from "node:net";
import os from "node:os";
import path from "node:path";
import { fileURLToPath } from "node:url";

import { expect, test } from "@playwright/test";

const webRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), "..");
const repoRoot = path.resolve(webRoot, "..");
const jarPath = path.join(repoRoot, "dist-server", "server.jar");
const javaHome = process.env.JAVA_HOME;
const javaBin = javaHome ? path.join(javaHome, "bin", "java") : "java";

async function freePort(): Promise<number> {
  return new Promise((resolve, reject) => {
    const s = net.createServer();
    s.listen(0, "127.0.0.1", () => {
      const addr = s.address();
      if (!addr || typeof addr === "string") {
        reject(new Error("no port"));
        return;
      }
      const port = addr.port;
      s.close(() => resolve(port));
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
      shopName: "E2E Shop",
      gstin: "",
      stateCode: "32",
      adminName: "Owner",
      username: "owner",
      password: "secret1",
    }),
  });
  expect(res.ok).toBeTruthy();
}

test.describe("billing smoke", () => {
  let server: ChildProcessWithoutNullStreams;
  let dataDir: string;
  let adminPort: number;
  let billingPort: number;

  test.beforeAll(async () => {
    if (!fs.existsSync(jarPath)) {
      throw new Error(`Missing ${jarPath}. Run ./scripts/build-server.sh first.`);
    }
    dataDir = fs.mkdtempSync(path.join(os.tmpdir(), "openbill-e2e-"));
    fs.mkdirSync(path.join(dataDir, "logs"), { recursive: true });
    adminPort = await freePort();
    billingPort = await freePort();
    while (billingPort === adminPort) {
      billingPort = await freePort();
    }

    server = spawn(
      javaBin,
      [
        "-jar",
        jarPath,
        `--app.admin-port=${adminPort}`,
        `--app.billing-port=${billingPort}`,
        `--app.public-port=${await freePort()}`,
      ],
      {
        env: { ...process.env, APP_DATA_DIR: dataDir },
        stdio: "pipe",
      },
    );

    await waitForHealth(`http://127.0.0.1:${billingPort}/api/health`);
    await setupShop(adminPort);
  });

  test.afterAll(async () => {
    if (server && !server.killed) {
      server.kill("SIGTERM");
      await new Promise<void>((resolve) => server.on("exit", () => resolve()));
    }
  });

  test("login and main billing pages render without console errors", async ({ browser }) => {
    const consoleErrors: string[] = [];
    const context = await browser.newContext({
      baseURL: `http://127.0.0.1:${billingPort}`,
    });
    context.on("page", (page) => {
      page.on("console", (msg) => {
        if (msg.type() === "error") consoleErrors.push(msg.text());
      });
    });
    const page = await context.newPage();
    page.on("console", (msg) => {
      if (msg.type() === "error") consoleErrors.push(msg.text());
    });

    await page.goto("/");
    await page.getByLabel("Username").fill("owner");
    await page.getByLabel("Password or PIN").fill("secret1");
    await page.getByRole("button", { name: /sign in/i }).click();

    await expect(page.getByRole("heading", { name: "New bill" })).toBeVisible();

    for (const route of ["/invoices", "/items", "/customers", "/reports"]) {
      consoleErrors.length = 0;
      await page.goto(route);
      await expect(page.locator("body")).not.toBeEmpty();
      await expect(page.getByRole("navigation", { name: "Main" })).toBeVisible();
      expect(consoleErrors.filter((e) => !e.includes("favicon"))).toEqual([]);
    }

    await context.close();
  });
});
