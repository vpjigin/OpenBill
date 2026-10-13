import { useCallback, useEffect, useState } from "react";
import { PageHeader } from "../../shared/ui";
import { activityActionLabels, activityChannelLabel } from "../activityLabels";
import { adminApi, type ActivityEntry } from "../adminApi";

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

export function ActivityPage() {
  const [from, setFrom] = useState(() => {
    const d = new Date();
    d.setDate(d.getDate() - 7);
    return d.toISOString().slice(0, 10);
  });
  const [to, setTo] = useState(todayIso);
  const [action, setAction] = useState("");
  const [user, setUser] = useState("");
  const [rows, setRows] = useState<ActivityEntry[]>([]);

  const load = useCallback(() => {
    adminApi
      .activity({ from, to, action: action || undefined, user: user || undefined, limit: 200 })
      .then(setRows)
      .catch(() => setRows([]));
  }, [from, to, action, user]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <>
      <PageHeader title="Activity log" subtitle="Who did what, and from where (kept 1 year)" />
      <div className="mb-4 flex flex-wrap items-end gap-3 text-sm">
        <label className="flex flex-col gap-1">
          <span className="text-muted">From</span>
          <input type="date" className="rounded-lg border border-line px-2 py-1.5" value={from} onChange={(e) => setFrom(e.target.value)} />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-muted">To</span>
          <input type="date" className="rounded-lg border border-line px-2 py-1.5" value={to} onChange={(e) => setTo(e.target.value)} />
        </label>
        <label className="flex flex-col gap-1">
          <span className="text-muted">Action</span>
          <select className="rounded-lg border border-line px-2 py-1.5" value={action} onChange={(e) => setAction(e.target.value)}>
            <option value="">All</option>
            {Object.keys(activityActionLabels).map((a) => (
              <option key={a} value={a}>
                {activityActionLabels[a]}
              </option>
            ))}
          </select>
        </label>
        <label className="flex min-w-[140px] flex-col gap-1">
          <span className="text-muted">User / target</span>
          <input
            type="search"
            className="rounded-lg border border-line px-2 py-1.5"
            value={user}
            onChange={(e) => setUser(e.target.value)}
            placeholder="Search…"
          />
        </label>
      </div>
      <div className="overflow-x-auto rounded-xl border border-line bg-surface">
        <table className="w-full min-w-[720px] border-collapse text-sm">
          <thead>
            <tr className="text-left text-xs uppercase tracking-wider text-muted">
              <th className="px-4 py-2.5">Time</th>
              <th className="px-2 py-2.5">User</th>
              <th className="px-2 py-2.5">Via</th>
              <th className="px-2 py-2.5">Action</th>
              <th className="px-2 py-2.5">Target</th>
              <th className="px-4 py-2.5">Details</th>
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 ? (
              <tr>
                <td colSpan={6} className="px-4 py-8 text-center text-muted">
                  No activity in this range
                </td>
              </tr>
            ) : (
              rows.map((r) => (
                <tr key={r.id} className="border-t border-line-soft">
                  <td className="whitespace-nowrap px-4 py-2.5 text-[13px] text-muted">
                    {new Date(r.createdAt).toLocaleString("en-IN")}
                  </td>
                  <td className="px-2 py-2.5">{r.userName ?? "—"}</td>
                  <td className="px-2 py-2.5 text-[13px] text-muted">
                    {r.channel ? activityChannelLabel[r.channel] : "—"}
                  </td>
                  <td className="px-2 py-2.5">{activityActionLabels[r.action] ?? r.action}</td>
                  <td className="max-w-[160px] truncate px-2 py-2.5 font-mono text-[12px]" title={r.target ?? undefined}>
                    {r.target ?? "—"}
                  </td>
                  <td className="max-w-[200px] truncate px-4 py-2.5 text-[13px] text-muted" title={r.details ?? undefined}>
                    {r.details ?? "—"}
                  </td>
                </tr>
              ))
            )}
          </tbody>
        </table>
      </div>
    </>
  );
}

export function ActivityTableCompact({ rows }: { rows: ActivityEntry[] }) {
  return (
    <table className="w-full border-collapse text-sm">
      <tbody>
        {rows.map((r) => (
          <tr key={r.id} className="border-t border-line-soft first:border-t-0">
            <td className="py-2 pr-2 text-[13px] text-muted whitespace-nowrap">
              {new Date(r.createdAt).toLocaleString("en-IN", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" })}
            </td>
            <td className="py-2 pr-2">{r.userName ?? "—"}</td>
            <td className="py-2 pr-2 text-[13px] text-muted">{r.channel ? activityChannelLabel[r.channel] : "—"}</td>
            <td className="py-2">{activityActionLabels[r.action] ?? r.action}</td>
            <td className="max-w-[120px] truncate py-2 font-mono text-[12px] text-muted">{r.target ?? ""}</td>
          </tr>
        ))}
      </tbody>
    </table>
  );
}
