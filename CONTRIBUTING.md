# Contributing

Most work never touches Rust: the server is Java and both UIs are React.

## Setup

| You're working on | You need |
|---|---|
| Server (`server/`) | JDK 21, Maven |
| Admin / billing UI (`web/`) | Node 20+ (plus the server running) |
| Desktop shell (`desktop/`) | Rust stable + [Tauri prerequisites](https://v2.tauri.app/start/prerequisites/) for your OS |

See the README's **Quick start** to run everything.

## Rules that keep the data correct

- **Money in paise (long/integer), GST in basis points.** Never `double`/`float` for amounts.
- **Every new table** gets `id TEXT` (UUIDv7, set by `BaseEntity`), `created_at`, `updated_at`,
  `version`, `deleted_at`. Extend `BaseEntity`.
- **Schema changes are new Flyway files** (`V2__...sql`). Never edit a migration that has shipped —
  real shops already ran it.
- **Never update or delete an invoice.** Add a credit note instead.
- **Anything admin-only** goes under `/api/admin/**` so it stays on the localhost admin port.

## Tests

```bash
cd server && mvn verify     # unit + end-to-end flow on a temp SQLite file
cd web && npm run build     # typecheck + build
```

CI runs both, and compiles the desktop shell, on every pull request.
