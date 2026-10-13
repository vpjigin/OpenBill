import { useCallback, useEffect, useState } from "react";
import { APP_NAME } from "../../shared/brand";
import { DataTable } from "../../shared/layout";
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
        subtitle={`${APP_NAME} bills in the browser from this computer. Data never leaves this PC.`}
      />
      <AccessTiles />
      <Card actions={<span className="text-[13px] text-muted">Stopping a channel signs out its devices</span>}>
        <div className="px-2 pb-4 sm:px-4">
          <h2 className="m-0 mb-3 px-2 text-base font-semibold sm:px-0">Signed-in devices</h2>
          <DataTable
            columns={[
              {
                key: "user",
                header: "User",
                mobilePrimary: true,
                cell: (d) => (
                  <>
                    {d.userName} <span className="text-xs text-muted">· {d.role.toLowerCase()}</span>
                  </>
                ),
              },
              { key: "via", header: "Via", cell: (d) => via[d.channel] },
              { key: "ip", header: "IP", hideOnMobile: true, cell: (d) => <span className="font-mono text-[13px]">{d.ip ?? "—"}</span> },
              {
                key: "device",
                header: "Device",
                hideOnMobile: true,
                cell: (d) => (
                  <span className="max-w-[220px] truncate text-muted" title={d.userAgent ?? ""}>
                    {shortAgent(d.userAgent)}
                  </span>
                ),
              },
              {
                key: "seen",
                header: "Last seen",
                cell: (d) => new Date(d.lastSeen).toLocaleTimeString("en-IN", { hour: "2-digit", minute: "2-digit" }),
              },
            ]}
            rows={devices}
            rowKey={(d) => d.id}
            emptyMessage="No one is signed in to the billing website."
            rowActions={(d) => (
              <Button variant="danger" className="min-h-11 px-3 text-[13px]" onClick={() => void adminApi.signOutDevice(d.id).then(load)}>
                Sign out
              </Button>
            )}
          />
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
