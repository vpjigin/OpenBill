# OpenBill — development guide

## Stack

| Part | Tech |
|------|------|
| Desktop shell | Tauri 2 (native window + tray, installers) |
| Server | Java 21, Spring Boot 3.5, Spring Security, JPA |
| Database | SQLite (WAL) + Flyway migrations |
| Web | React 19, Vite, Tailwind 4 |

## Project layout

```
server/    Spring Boot API + serves both web apps
  channel/   ports → channels, Wi-Fi/public start-stop, tunnel
  auth/      tokens, login throttle
  invoice/   GST math, numbering, immutable invoices
web/       React: src/admin (desktop admin) and src/billing (billing website)
desktop/   Tauri shell: starts the server, window, tray
scripts/   build-server.sh (jar + jlink), run-desktop.sh (rebuild + launch + asset check)
.github/   CI (every push) and Release (tags)
```

## Local development

```bash
# 1. Server (creates ~/.billing-app/billing.db or APP_DATA_DIR)
cd server && mvn spring-boot:run

# 2a. Admin app → http://localhost:5174/admin.html
cd web && npm run dev:admin

# 2b. Billing website → http://localhost:5173
cd web && npm run dev:billing

# 3. Desktop (needs bundled server from scripts/build-server.sh for a full run)
cd desktop && npm run dev
```

Build everything:

```bash
cd web && npm ci && npm run build
cd server && mvn -q verify
./scripts/build-server.sh
cd desktop && npm ci && npm run build
```

### Run the desktop app locally

On **macOS or Linux**, `./scripts/run-desktop.sh` stops any old OpenBill, rebuilds the bundled server and desktop app, launches it, and checks that billing on port 8080 serves the same `index-*.js` as the jar inside the app.

Needs JDK 21, Maven, Node 20+, Rust, `curl`, `unzip`, and `lsof`. Admin: `http://127.0.0.1:9090`; billing: `http://127.0.0.1:8080`.

On Windows, run `./scripts/build-server.sh` then `cd desktop && npm run build` manually.

## Tests

```bash
cd web && npm run lint && npm run build
cd server && mvn -q verify
cd web && npm run e2e:responsive   # optional; needs dist-server/server.jar
```

CI runs web lint/build, server tests, desktop compile, and `actionlint` on workflows.

## Release process

1. Bump version everywhere: `./scripts/bump-version.sh x.y.z`
2. Commit and push tag `vx.y.z` (must match Tauri `version`).
3. GitHub Actions **Release** workflow builds installers on Windows, macOS (Apple Silicon + Intel), and Linux.
4. Each matrix job uploads versioned artifacts via `tauri-action`, then **stable aliases** (e.g. `OpenBill-macOS-AppleSilicon.dmg`) for `releases/latest/download/…`.
5. A final **Publish release** job sets `draft=false` when all builds succeed.

Optional signing: set repository secrets for Apple / Windows / Tauri updater signing — see [Tauri signing docs](https://v2.tauri.app/distribute/sign/). Never commit certificates.

## Public access (optional)

1. **Quick tunnel:** Admin → Server & access → start **Public link** (`*.trycloudflare.com` while running).
2. **Production:** named Cloudflare tunnel to `http://127.0.0.1:8081`, token and URL in shop settings.

Only users with **Allow web sign-in** can log in over Wi‑Fi or public. Login is rate-limited (5 wrong attempts → 5-minute lock).

## Branding

- Product name: **OpenBill** (`application.yml`, `web/src/shared/brand.ts`, Tauri `productName`).
- Desktop identifier: `io.github.vpjigin.openbill` (data folder on each OS).
- Shop logo: **Admin → Shop & GST settings** (PNG/JPEG, max 200 KB); on A4 and optional 80 mm prints.
