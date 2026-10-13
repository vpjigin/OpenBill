import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import { Button, ErrorNote, PageHeader, errorText } from "../../shared/ui";
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
          <Button variant="secondary" className="min-h-9 text-[13px]" onClick={() => void cancelPending()}>
            Cancel
          </Button>
        </div>
      )}

      <div className="mb-6 flex flex-wrap items-end gap-3">
        <div>
          <div className="text-xs text-muted">Last backup</div>
          <div className="font-mono text-lg">{formatWhen(list?.lastBackupAt ?? null)}</div>
        </div>
        <Button onClick={() => void backUpNow()} disabled={busy}>
          {busy ? "Working…" : "Back up now"}
        </Button>
        <Button variant="secondary" onClick={() => void adminApi.openBackupFolder().catch((e) => setError(errorText(e)))}>
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
          <Button variant="secondary" type="button" disabled={busy} onClick={() => fileRef.current?.click()}>
            Upload backup
          </Button>
        </label>
      </div>

      <div className="overflow-x-auto rounded-xl border border-line bg-surface">
        <table className="w-full min-w-[520px] border-collapse text-sm">
          <thead>
            <tr className="text-left text-xs uppercase tracking-wider text-muted">
              <th className="px-4 py-2">Name</th>
              <th className="px-2 py-2">Date</th>
              <th className="px-2 py-2 text-right">Size</th>
              <th className="px-4 py-2" />
            </tr>
          </thead>
          <tbody>
            {!list || list.backups.length === 0 ? (
              <tr>
                <td colSpan={4} className="px-4 py-8 text-center text-muted">
                  No backups yet
                </td>
              </tr>
            ) : (
              list.backups.map((f) => (
                <tr key={f.name} className="border-t border-line-soft">
                  <td className="px-4 py-2 font-mono text-[13px]">{f.name}</td>
                  <td className="px-2 py-2">{formatWhen(f.createdAt)}</td>
                  <td className="px-2 py-2 text-right font-mono">{formatBytes(f.size)}</td>
                  <td className="px-4 py-2 text-right">
                    {restorable(f) && (
                      <Button variant="secondary" className="min-h-9 px-3 text-[13px]" onClick={() => void restore(f.name)}>
                        Restore
                      </Button>
                    )}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>

      <p className="mt-4 text-sm text-muted">
        Automatic backups run about once a day when the app is open.{" "}
        <Link to="/" className="text-brand">
          Dashboard
        </Link>
      </p>
    </>
  );
}
