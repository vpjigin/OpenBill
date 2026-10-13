import { useCallback, useEffect, useState } from "react";
import { DataTable, Toolbar, ToolbarField } from "../../shared/layout";
import { Card, PageHeader } from "../../shared/ui";
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
      <Card
        actions={
          <Toolbar>
            <ToolbarField label="From">
              <input type="date" className="rounded-lg border border-line px-2" value={from} onChange={(e) => setFrom(e.target.value)} />
            </ToolbarField>
            <ToolbarField label="To">
              <input type="date" className="rounded-lg border border-line px-2" value={to} onChange={(e) => setTo(e.target.value)} />
            </ToolbarField>
            <ToolbarField label="Action">
              <select className="rounded-lg border border-line px-2" value={action} onChange={(e) => setAction(e.target.value)}>
                <option value="">All</option>
                {Object.keys(activityActionLabels).map((a) => (
                  <option key={a} value={a}>
                    {activityActionLabels[a]}
                  </option>
                ))}
              </select>
            </ToolbarField>
            <ToolbarField label="User / target">
              <input
                type="search"
                className="rounded-lg border border-line px-2"
                value={user}
                onChange={(e) => setUser(e.target.value)}
                placeholder="Search…"
              />
            </ToolbarField>
          </Toolbar>
        }
      >
        <div className="px-2 pb-4 sm:px-4">
          <DataTable
            columns={[
              {
                key: "time",
                header: "Time",
                mobilePrimary: true,
                cell: (r) => (
                  <span className="text-[13px] text-muted">{new Date(r.createdAt).toLocaleString("en-IN")}</span>
                ),
              },
              { key: "user", header: "User", cell: (r) => r.userName ?? "—" },
              {
                key: "via",
                header: "Via",
                hideOnMobile: true,
                cell: (r) => (r.channel ? activityChannelLabel[r.channel] : "—"),
              },
              { key: "action", header: "Action", cell: (r) => activityActionLabels[r.action] ?? r.action },
              {
                key: "target",
                header: "Target",
                cell: (r) => (
                  <span className="max-w-[160px] truncate font-mono text-[12px]" title={r.target ?? undefined}>
                    {r.target ?? "—"}
                  </span>
                ),
              },
              {
                key: "details",
                header: "Details",
                hideOnMobile: true,
                cell: (r) => (
                  <span className="max-w-[200px] truncate text-[13px] text-muted" title={r.details ?? undefined}>
                    {r.details ?? "—"}
                  </span>
                ),
              },
            ]}
            rows={rows}
            rowKey={(r) => r.id}
            emptyMessage="No activity in this range"
          />
        </div>
      </Card>
    </>
  );
}

export function ActivityTableCompact({ rows }: { rows: ActivityEntry[] }) {
  return (
    <>
      <ul className="m-0 list-none divide-y divide-line-soft sm:hidden">
        {rows.map((r) => (
          <li key={r.id} className="py-3 text-sm">
            <div className="font-medium">{activityActionLabels[r.action] ?? r.action}</div>
            <div className="mt-1 text-[13px] text-muted">
              {r.userName ?? "—"} · {r.channel ? activityChannelLabel[r.channel] : "—"}
            </div>
            <div className="mt-0.5 text-xs text-muted">
              {new Date(r.createdAt).toLocaleString("en-IN", { month: "short", day: "numeric", hour: "2-digit", minute: "2-digit" })}
            </div>
          </li>
        ))}
      </ul>
      <table className="hidden w-full border-collapse text-sm sm:table">
        <tbody>
          {rows.map((r) => (
            <tr key={r.id} className="border-t border-line-soft first:border-t-0">
              <td className="py-2 pr-2 text-[13px] text-muted">
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
    </>
  );
}
