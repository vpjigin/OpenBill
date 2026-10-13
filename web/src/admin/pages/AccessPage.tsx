import { useCallback, useEffect, useState } from "react";
import { Button, Card, PageHeader } from "../../shared/ui";
import { AccessTiles } from "../AccessTiles";
import { adminApi, type Device } from "../adminApi";

const via: Record<Device["channel"], string> = { ADMIN: "Admin", LOCAL: "This computer", LAN: "Wi-Fi", PUBLIC: "Public" };

export function AccessPage() {
  const [devices, setDevices] = useState<Device[]>([]);
  const load = useCallback(() => {
    adminApi.devices().then(setDevices).catch(() => undefined);
  }, []);

  useEffect(() => {
    load();
    const t = setInterval(load, 10_000);
    return () => clearInterval(t);
  }, [load]);

  return (
    <>
      <PageHeader
        title="Server & access"
        subtitle="Billing is a website served from this computer. Data never leaves this PC."
      />
      <AccessTiles />
      <Card title="Signed-in devices" actions={<span className="text-[13px] text-muted">Stopping a channel signs out its devices</span>}>
        <div className="overflow-x-auto">
          <table className="w-full min-w-[640px] border-collapse text-sm">
            <thead>
              <tr className="text-left text-xs uppercase tracking-wider text-muted">
                <th className="px-5 py-2.5 font-medium">User</th>
                <th className="px-3 py-2.5 font-medium">Via</th>
                <th className="px-3 py-2.5 font-medium">IP</th>
                <th className="px-3 py-2.5 font-medium">Device</th>
                <th className="px-3 py-2.5 font-medium">Last seen</th>
                <th className="px-5 py-2.5 text-right font-medium">Action</th>
              </tr>
            </thead>
            <tbody>
              {devices.length === 0 && (
                <tr className="border-t border-line-soft">
                  <td colSpan={6} className="px-5 py-4 text-muted">
                    No one is signed in to the billing website.
                  </td>
                </tr>
              )}
              {devices.map((d) => (
                <tr key={d.id} className="border-t border-line-soft">
                  <td className="px-5 py-3">
                    {d.userName} <span className="text-xs text-muted">· {d.role.toLowerCase()}</span>
                  </td>
                  <td className="px-3 py-3">{via[d.channel]}</td>
                  <td className="px-3 py-3 font-mono text-[13px]">{d.ip ?? "—"}</td>
                  <td className="max-w-[220px] truncate px-3 py-3 text-muted" title={d.userAgent ?? ""}>
                    {shortAgent(d.userAgent)}
                  </td>
                  <td className="px-3 py-3">{new Date(d.lastSeen).toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" })}</td>
                  <td className="px-5 py-3 text-right">
                    <Button variant="danger" className="min-h-9 px-3 text-[13px]" onClick={() => adminApi.signOutDevice(d.id).then(load)}>
                      Sign out
                    </Button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      </Card>
    </>
  );
}

function shortAgent(ua: string | null): string {
  if (!ua) return "—";
  const os = /Android/.test(ua) ? "Android" : /iPhone|iPad/.test(ua) ? "iPhone" : /Windows/.test(ua) ? "Windows" : /Mac OS/.test(ua) ? "Mac" : /Linux/.test(ua) ? "Linux" : "";
  const br = /Edg\//.test(ua) ? "Edge" : /Chrome\//.test(ua) ? "Chrome" : /Firefox\//.test(ua) ? "Firefox" : /Safari\//.test(ua) ? "Safari" : "Browser";
  return [os, br].filter(Boolean).join(" · ");
}
