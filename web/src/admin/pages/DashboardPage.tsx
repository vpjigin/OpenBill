import { useEffect, useState, type ReactNode } from "react";
import { Link, useNavigate } from "react-router-dom";
import { formatPaise } from "../../shared/money";
import { PageHeader } from "../../shared/ui";
import { OpenBillingButton } from "../AdminApp";
import { AccessTiles } from "../AccessTiles";
import { adminApi, type ActivityEntry, type AttentionItem, type Summary } from "../adminApi";
import { ActivityTableCompact } from "./ActivityPage";

function backupStale(lastBackupAt: string | null): boolean {
  if (!lastBackupAt) return true;
  return Date.now() - new Date(lastBackupAt).getTime() > 48 * 60 * 60 * 1000;
}

function formatBackupWhen(iso: string | null): string {
  if (!iso) return "Never";
  return new Date(iso).toLocaleString("en-IN", { day: "numeric", month: "short", hour: "2-digit", minute: "2-digit" });
}

export function DashboardPage() {
  const navigate = useNavigate();
  const [summary, setSummary] = useState<Summary | null>(null);
  const [recentActivity, setRecentActivity] = useState<ActivityEntry[]>([]);
  const [attention, setAttention] = useState<AttentionItem[]>([]);

  useEffect(() => {
    const load = () => {
      adminApi.summary().then(setSummary).catch(() => undefined);
      adminApi.activity({ limit: 8 }).then(setRecentActivity).catch(() => setRecentActivity([]));
      adminApi.attention().then(setAttention).catch(() => setAttention([]));
    };
    load();
    const t = setInterval(load, 30_000);
    return () => clearInterval(t);
  }, []);

  const today = new Date().toLocaleDateString("en-IN", { weekday: "long", day: "numeric", month: "short", year: "numeric" });

  return (
    <>
      <PageHeader
        title="Dashboard"
        subtitle={`${summary?.shopName ?? ""} · ${today}`}
        actions={<OpenBillingButton large />}
      />

      <section className="flex flex-col gap-3">
        <div className="flex items-baseline justify-between gap-3">
          <h2 className="m-0 text-[17px] font-semibold">Access</h2>
          <Link to="/access" className="text-[13px] text-brand">
            Server details
          </Link>
        </div>
        <AccessTiles />
      </section>

      <section className="flex flex-col gap-3">
        <h2 className="m-0 text-[17px] font-semibold">Needs attention</h2>
        <ul className="m-0 list-none space-y-2 rounded-xl border border-line bg-surface p-4">
          {attention.length === 0 ? (
            <li className="text-sm text-muted">All clear</li>
          ) : (
            attention.map((a) => (
              <li key={a.code + a.message} className="flex items-start gap-2.5 text-sm">
                <span
                  className={`mt-1.5 size-2 shrink-0 rounded-full ${a.level === "warn" ? "bg-amber-500" : "bg-emerald-500"}`}
                  aria-hidden
                />
                <span>{a.message}</span>
              </li>
            ))
          )}
        </ul>
      </section>

      <section className="flex flex-col gap-3">
        <div className="flex items-baseline justify-between gap-3">
          <h2 className="m-0 text-[17px] font-semibold">Recent activity</h2>
          <Link to="/activity" className="text-[13px] text-brand">
            View all
          </Link>
        </div>
        <div className="rounded-xl border border-line bg-surface px-4 py-2">
          {recentActivity.length === 0 ? (
            <p className="m-0 py-4 text-center text-sm text-muted">No recent activity</p>
          ) : (
            <ActivityTableCompact rows={recentActivity} />
          )}
        </div>
      </section>

      <section className="flex flex-col gap-3">
        <div className="flex items-baseline justify-between gap-3">
          <h2 className="m-0 text-[17px] font-semibold">Today at a glance</h2>
          <span className="text-[13px] text-muted">Read-only. Full reports are in the billing website.</span>
        </div>
        <div className="grid grid-cols-[repeat(auto-fit,minmax(180px,1fr))] gap-3.5">
          <Stat label="Bills today" value={summary ? String(summary.billsToday) : "—"} />
          <Stat label="Sales today" value={summary ? formatPaise(summary.salesTodayPaise) : "—"} />
          <Stat label="Devices signed in" value={summary ? String(summary.devicesOnline) : "—"} />
          <Stat
            label="Last backup"
            value={summary ? formatBackupWhen(summary.lastBackupAt) : "—"}
            warn={summary ? backupStale(summary.lastBackupAt) : false}
            action={
              <button type="button" className="text-[13px] text-brand" onClick={() => navigate("/backup")}>
                Back up now
              </button>
            }
          />
        </div>
      </section>
    </>
  );
}

function Stat({
  label,
  value,
  warn,
  action,
}: {
  label: string;
  value: string;
  warn?: boolean;
  action?: ReactNode;
}) {
  return (
    <div
      className={`flex flex-col gap-1.5 rounded-xl border bg-surface px-4.5 py-4 ${warn ? "border-amber-500/50" : "border-line"}`}
    >
      <span className="text-[13px] text-muted">{label}</span>
      <span className={`font-mono text-[26px] font-medium ${warn ? "text-amber-800" : ""}`}>{value}</span>
      {action}
    </div>
  );
}
