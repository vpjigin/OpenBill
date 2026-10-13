# OpenBill

Offline GST billing for Indian shops. Open source ([MIT](LICENSE)), runs on **Windows, macOS and Linux**.

Repository: https://github.com/vpjigin/OpenBill

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
| Web | React 19, Vite, Tailwind 4 |

## Development

```bash
# 1. Server (creates ~/.billing-app/billing.db)
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
cd desktop && npm ci && npm run build   # after ./scripts/build-server.sh
```

## Signing (optional)

Installers can be signed on CI when repository secrets are set — see [Tauri signing docs](https://v2.tauri.app/distribute/sign/).

Never commit certificates or passwords. Add them only as repository **Secrets**.

## Public access (optional)

1. **Quick tunnel:** Admin → Server & access → start **Public link**. Uses a random `*.trycloudflare.com` URL while running.
2. **Production:** create a named tunnel whose public hostname points to `http://127.0.0.1:8081`,
   paste its token and URL in settings. The link then stays fixed.

Only users with **Allow web sign-in** can log in over Wi-Fi or public. Login is rate-limited
(5 wrong attempts → 5-minute lock).

## Where data lives

| OS | Folder |
|---|---|
| Windows | `%APPDATA%\io.github.vpjigin.openbill\` |
| macOS | `~/Library/Application Support/io.github.vpjigin.openbill/` |
| Linux | `~/.local/share/io.github.vpjigin.openbill/` |

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

- Product name: **OpenBill** (`app.name` in `server/src/main/resources/application.yml`, `web/src/shared/brand.ts`, Tauri `productName`).
- Tagline: **Offline GST billing for Indian shops**
- Desktop identifier: `io.github.vpjigin.openbill` (data folder name on each OS).
- Shop logo: **Admin → Shop & GST settings** (PNG/JPEG, max 200 KB); appears on A4 and optional 80 mm prints.

## License

[MIT](LICENSE). Bundled **cloudflared** is Apache-2.0 — see [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
