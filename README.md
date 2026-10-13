# Billing

Offline-first GST billing for shops. Open source ([MIT](LICENSE)), runs on **Windows, macOS and Linux**.

Repository: https://github.com/billing-app/billing-app

- **Desktop app** (admin only): installs like any app, runs the server, manages users and access.
- **Billing website**: every bill is made in a browser — on the same computer, on any device on the shop
  Wi-Fi, or from anywhere through a public link.
- **No internet needed** for billing. All data lives in one SQLite file on the shop computer.

```
 ┌──────────────── Shop computer (desktop app) ─────────────────┐
 │  Tauri window ──► 127.0.0.1:9090  Admin app  (admin only)     │
 │                                                                │
 │  Spring Boot server ─── SQLite file (billing.db)               │
 │     ├─ 127.0.0.1:8080       Billing website · this computer   │  always on
 │     ├─ <LAN IP>:8080        Billing website · Wi-Fi / LAN     │  Start / Stop
 │     └─ 127.0.0.1:8081 ◄── cloudflared ◄── Public link         │  Start / Stop
 └────────────────────────────────────────────────────────────────┘
        ▲ counters, laptops, phones on the Wi-Fi      ▲ owner from anywhere
```

## Stack

| Part | Tech |
|---|---|
| Desktop shell | Tauri 2 (native window + tray, small installers) |
| Server | Java 21, Spring Boot 3.5, Spring Security, JPA |
| Database | SQLite (WAL) + Flyway migrations |
| Web apps | React 19 + Vite + TypeScript + Tailwind 4 (admin and billing from one codebase) |
| Java runtime | Bundled per OS with `jlink` — users never install Java |
| Releases | GitHub Actions → `.msi/.exe`, `.dmg` (Apple Silicon + Intel), `.deb/.rpm/.AppImage` |

## Design decisions

- **Ports, not headers, decide what's reachable.** The admin API and admin UI exist only on
  `127.0.0.1:9090`. Wi-Fi and public requests can't reach them at all (`ChannelFilter`).
- **Tokens are bound to the channel** they were issued on — a Wi-Fi token can't be replayed on the admin port.
- **UUIDv7 primary keys** (time-ordered) on every table, plus `created_at`, `updated_at`, `version`
  (optimistic locking) and `deleted_at` (soft delete) — ready for multi-branch sync later.
- **Invoice numbers are separate** from IDs: `INV/26-27/000001`, gap-free, restarting every April (GST rule).
  The number is taken as the first write of the save transaction so two counters can't collide.
- **Money is integer paise**, GST rates are basis points (12% = 1200). No floating point anywhere.
- **Invoices are immutable** — there is no edit endpoint. Corrections will be credit notes.
- **Prices come from the server** at save time; the browser's numbers are only a preview.

## Quick start (development)

Prerequisites: JDK 21, Maven, Node 20+. Rust only if you work on the desktop shell.

```bash
# 1. Server (creates ~/.billing-app/billing.db)
cd server
mvn spring-boot:run

# 2a. Admin app  → http://localhost:5174/admin.html
cd web && npm install && npm run dev:admin

# 2b. Billing website → http://localhost:5173
cd web && npm run dev:billing
```

First run of the admin app shows the **setup** screen (shop name + owner account).
Then add items from the billing website (**Items & stock**, manager/admin only) and make a bill.

**GSTR-1:** Managers and admins can download a monthly zip from **Reports → GSTR-1** on the billing site.
Check the CSV columns against the current GST offline tool template before filing.

**E-invoice (manual upload):** **Reports → E-invoice** exports JSON for B2B bills without an IRN; after the portal
returns IRN/Ack/QR, enter them on the invoice detail page. See `server/src/main/java/app/billing/einvoice/README.md`
— verify the field list against the current NIC schema before use.

### Run the desktop app in dev

```bash
cd server && mvn spring-boot:run          # keep running
cd desktop && npm install && npm run dev  # opens the window, finds the server on :9090
```

In dev the shell doesn't start Java itself (nothing bundled yet) — it uses the server you started.

## Build an installer locally

```bash
./scripts/build-server.sh   # web → server.jar → jlink runtime, into dist-server/
cd desktop && npm install && npm run build
```

Installers land in `desktop/src-tauri/target/release/bundle/`. You can only build for the OS you're on —
that's what the release workflow is for.

## Release (all three OSes)

1. `./scripts/bump-version.sh 0.2.0` — updates `tauri.conf.json`, `Cargo.toml`, `server/pom.xml`, and `web/package.json`.
2. `git tag v0.2.0 && git push --tags`
3. GitHub Actions builds every installer and creates a **draft release**. Review it and publish.

Regenerate desktop icons after changing `desktop/app-icon.png`:

```bash
cd desktop && npx tauri icon app-icon.png
```

### Signing (optional)

CI builds **unsigned** installers when signing secrets are missing. Users see normal OS warnings:

| OS | Without signing | With secrets in GitHub Actions |
|---|---|---|
| **macOS** | Gatekeeper blocks the app until the user right-clicks → **Open** once | Add `APPLE_CERTIFICATE` (base64 `.p12`), `APPLE_CERTIFICATE_PASSWORD`, `APPLE_SIGNING_IDENTITY`, `APPLE_ID`, `APPLE_PASSWORD`, `APPLE_TEAM_ID` — see [Tauri macOS signing](https://v2.tauri.app/distribute/sign/macos/) |
| **Windows** | SmartScreen “Unknown publisher” | Configure Windows signing per [Tauri Windows signing](https://v2.tauri.app/distribute/sign/windows/) (e.g. `TAURI_SIGNING_PRIVATE_KEY` + password for trusted signing pipelines) |
| **Linux** | No code signing expected | — |

Never commit certificates or passwords. Add them only as repository **Secrets**.

## Public access

Uses [Cloudflare Tunnel](https://developers.cloudflare.com/cloudflare-one/connections/connect-networks/)
(`cloudflared`), which works behind CGNAT with no router setup.

1. Put the `cloudflared` binary in `<data dir>/bin/` or on `PATH` (later: bundle it per OS in CI).
2. **Testing:** leave the tunnel token empty in **Shop & GST settings** → *Start* gives a temporary
   `*.trycloudflare.com` link (changes every start).
3. **Production:** create a named tunnel whose public hostname points to `http://127.0.0.1:8081`,
   paste its token and URL in settings. The link then stays fixed.

Only users with **Allow web sign-in** can log in over Wi-Fi or public. Login is rate-limited
(5 wrong attempts → 5-minute lock).

## Where data lives

| OS | Folder |
|---|---|
| Windows | `%APPDATA%\app.billing.desktop\` |
| macOS | `~/Library/Application Support/app.billing.desktop/` |
| Linux | `~/.local/share/app.billing.desktop/` |

`billing.db` (+ `-wal`, `-shm`), `backups/` (scheduled copies), and `logs/server.log`.
Use **Admin → Backup** to back up, upload, or schedule a restore (applied on next app start).

## Project layout

```
server/    Spring Boot API + serves both web apps
  channel/   ports → channels, Wi-Fi/public start-stop, tunnel
  auth/      tokens, login throttle
  invoice/   GST math, numbering, immutable invoices
web/       React: src/admin (desktop admin) and src/billing (billing website)
desktop/   Tauri shell: starts the server, window, tray
scripts/   build-server.sh (jar + jlink runtime)
.github/   CI (every push) and Release (tags)
```

## Branding

- Product name: **Billing** (`app.name` in `server/src/main/resources/application.yml`, `web/src/shared/brand.ts`, Tauri `productName`).
- Desktop identifier: `app.billing.desktop` (data folder name on each OS — change only with a migration plan).
- Shop logo: **Admin → Shop & GST settings** (PNG/JPEG, max 200 KB); appears on A4 and optional 80 mm prints.

## License

[MIT](LICENSE). Bundled **cloudflared** is Apache-2.0 — see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
