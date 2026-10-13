import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { DataTable, StatCard, StatGrid } from "../../shared/layout";
import { Button, Card, ErrorNote, PageHeader, errorText } from "../../shared/ui";
import { adminApi, type BackupFile, type BackupList } from "../adminApi";

function formatBytes(n: number): string {
  if (n >= 1_000_000_000) return `${(n / 1_000_000_000).toFixed(1)} GB`;
  if (n >= 1_000_000) return `${(n / 1_000_000).toFixed(1)} MB`;
  if (n >= 1_000) return `${(n / 1_000).toFixed(0)} KB`;
  return `${n} B`;
}

function formatWhen(iso: string | null): string {
  if (!iso) return "Never";
  return new Date(iso).toLocaleString("en-IN", {
    day: "numeric",
    month: "short",
    year: "numeric",
    hour: "2-digit",
    minute: "2-digit",
  });
}

export function BackupPage() {
  const [list, setList] = useState<BackupList | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const fileRef = useRef<HTMLInputElement>(null);

  const load = useCallback(() => {
    adminApi.backups().then(setList).catch((e) => setError(errorText(e)));
  }, []);

  useEffect(() => {
    load();
  }, [load]);

  async function backUpNow() {
    setError(null);
    setBusy(true);
    try {
      await adminApi.createBackup();
      load();
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  }

  async function onUpload(file: File | undefined) {
    if (!file) return;
    setError(null);
    setBusy(true);
    try {
      await adminApi.uploadBackup(file);
      load();
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(false);
      if (fileRef.current) fileRef.current.value = "";
    }
  }

  async function restore(name: string) {
    const ok = window.confirm(
      "The app will switch to this backup the next time it starts. A safety copy of today's data is kept.",
    );
    if (!ok) return;
    setError(null);
    try {
      await adminApi.restoreBackup(name);
      load();
    } catch (e) {
      setError(errorText(e));
    }
  }

  async function cancelPending() {
    setError(null);
    try {
      await adminApi.cancelRestorePending();
      load();
    } catch (e) {
      setError(errorText(e));
    }
  }

  const restorable = (f: BackupFile) => f.kind === "backup" || f.kind === "upload";

  return (
    <>
      <PageHeader title="Backup" subtitle="SQLite copies of your billing data" />
      <ErrorNote message={error} />

      {list?.pendingRestore && (
        <div className="mb-4 rounded-xl border border-amber-500/40 bg-amber-500/10 px-4 py-3 text-sm">
          <p className="m-0 font-medium">Restore pending — quit and reopen the app</p>
          <p className="mt-1 mb-2 text-muted">Will restore: {list.pendingRestore}</p>
          <Button variant="secondary" className="min-h-11 text-[13px]" onClick={() => void cancelPending()}>
            Cancel
          </Button>
        </div>
      )}

      <StatGrid className="mb-4">
        <StatCard label="Last backup" value={formatWhen(list?.lastBackupAt ?? null)} />
        <StatCard label="Backup files" value={list?.backups.length ?? 0} />
      </StatGrid>

      <div className="mb-6 flex flex-wrap gap-2">
        <Button className="min-h-11" onClick={() => void backUpNow()} disabled={busy}>
          {busy ? "Working…" : "Back up now"}
        </Button>
        <Button variant="secondary" className="min-h-11" onClick={() => void adminApi.openBackupFolder().catch((e) => setError(errorText(e)))}>
          Open backup folder
        </Button>
        <label className="inline-flex cursor-pointer items-center">
          <input
            ref={fileRef}
            type="file"
            accept=".db,application/octet-stream"
            className="hidden"
            onChange={(e) => void onUpload(e.target.files?.[0])}
          />
          <Button variant="secondary" type="button" className="min-h-11" disabled={busy} onClick={() => fileRef.current?.click()}>
            Upload backup
          </Button>
        </label>
      </div>

      <Card>
        <div className="px-2 pb-4 sm:px-4">
          <DataTable
            columns={[
              { key: "name", header: "Name", mobilePrimary: true, cell: (f) => <span className="font-mono text-[13px]">{f.name}</span> },
              { key: "date", header: "Date", cell: (f) => formatWhen(f.createdAt) },
              { key: "size", header: "Size", mobileAmount: true, className: "text-right font-mono", cell: (f) => formatBytes(f.size) },
            ]}
            rows={list?.backups ?? []}
            rowKey={(f) => f.name}
            emptyMessage="No backups yet"
            rowActions={(f) =>
              restorable(f) ? (
                <Button variant="secondary" className="min-h-11 px-3 text-[13px]" onClick={() => void restore(f.name)}>
                  Restore
                </Button>
              ) : null
            }
          />
        </div>
      </Card>

      <p className="mt-4 text-sm text-muted">
        Automatic backups run about once a day when the app is open.{" "}
        <Link to="/" className="text-brand">
          Dashboard
        </Link>
      </p>
    </>
  );
}
