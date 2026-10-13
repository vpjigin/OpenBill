import { useState } from "react";
import { QRCodeSVG } from "qrcode.react";
import { Badge, Button } from "../shared/ui";
import type { ChannelStatus } from "./adminApi";

/**
 * One access channel: status, the link once started, Copy / QR / Open, and Start/Stop.
 * Used on the Dashboard and the Server & access page.
 */
export function AccessTile({
  title,
  description,
  status,
  alwaysOn = false,
  busy = false,
  onToggle,
  onOpen,
  stoppedHint,
  note,
}: {
  title: string;
  description: string;
  status: ChannelStatus | undefined;
  alwaysOn?: boolean;
  busy?: boolean;
  onToggle?: (run: boolean) => void;
  onOpen: (url: string) => void;
  stoppedHint: string;
  note?: string;
}) {
  const [showQr, setShowQr] = useState(false);
  const [copied, setCopied] = useState<string | null>(null);

  const running = alwaysOn || !!status?.running;
  const reconnecting = status?.state === "RECONNECTING";
  const connecting = status?.state === "CONNECTING" || reconnecting;
  const failed = status?.state === "ERROR";
  const linkKind = status?.linkKind;
  const urls = status?.urls ?? [];
  const primary = urls[0];

  async function copy(url: string) {
    try {
      await navigator.clipboard.writeText(url);
      setCopied(url);
      setTimeout(() => setCopied(null), 1500);
    } catch {
      window.prompt("Copy this link", url);
    }
  }

  const badge = alwaysOn ? (
    <Badge tone="ok">Always on</Badge>
  ) : failed ? (
    <Badge tone="warn">Problem</Badge>
  ) : reconnecting ? (
    <Badge tone="warn">Reconnecting</Badge>
  ) : connecting ? (
    <Badge tone="warn">Connecting</Badge>
  ) : running ? (
    <Badge tone="ok">Running</Badge>
  ) : (
    <Badge>Stopped</Badge>
  );

  return (
    <section
      className={`flex flex-col gap-3.5 rounded-xl bg-surface p-5 ${running && !failed ? "border-2 border-brand" : "border border-line"}`}
    >
      <div className="flex items-start justify-between gap-3">
        <div className="flex flex-col gap-1">
          <h2 className="m-0 text-[17px] font-semibold">{title}</h2>
          <span className="text-[13px] text-muted">{description}</span>
        </div>
        {badge}
      </div>

      {failed && status?.error && (
        <p className="m-0 rounded-lg bg-warn-bg px-3 py-2.5 text-[13px] text-warn-ink">{status.error}</p>
      )}

      {linkKind && running && (
        <p className="m-0 text-[13px] text-muted">
          {linkKind === "FIXED" ? "Fixed link — same URL every time." : "Temporary link — changes every start."}
        </p>
      )}

      {connecting && !primary && (
        <p className="m-0 rounded-lg bg-warn-bg px-3 py-2.5 text-[13px] text-warn-ink">
          {reconnecting || status?.error === "Reconnecting…"
            ? "Reconnecting…"
            : "Connecting to secure tunnel…"}
        </p>
      )}

      {reconnecting && status?.error && status.error !== "Reconnecting…" && (
        <p className="m-0 text-xs text-muted">Last error: {status.error}</p>
      )}

      {running && primary && (
        <>
          <div className="flex flex-col gap-1 rounded-lg bg-brand-soft px-3 py-2.5">
            <span className="text-[11px] uppercase tracking-[0.06em] text-[#3f4b45]">
              Link · {status?.devices ?? 0} device{status?.devices === 1 ? "" : "s"}
            </span>
            {urls.map((u) => (
              <span key={u} className="break-all font-mono text-[15px] font-medium text-brand">
                {u}
              </span>
            ))}
          </div>
          <div className="grid grid-cols-3 gap-1.5">
            <Button className="min-h-9 px-2 text-xs" onClick={() => copy(primary)}>
              {copied ? "Copied" : "Copy"}
            </Button>
            <Button className="min-h-9 px-2 text-xs" aria-pressed={showQr} onClick={() => setShowQr(!showQr)}>
              {showQr ? "Hide QR" : "QR"}
            </Button>
            <Button className="min-h-9 px-2 text-xs" onClick={() => onOpen(primary)}>
              Open
            </Button>
          </div>
          {showQr && (
            <div className="flex flex-col items-center gap-1.5 py-2">
              <QRCodeSVG value={primary} size={148} marginSize={2} title={`QR code for ${primary}`} />
              <span className="text-center text-xs text-muted">Scan with a phone to open the billing website</span>
            </div>
          )}
        </>
      )}

      {!running && <span className="py-2 text-[13px] text-muted">{stoppedHint}</span>}

      {note && running && <p className="m-0 text-xs leading-relaxed text-muted">{note}</p>}

      {!alwaysOn && onToggle && (
        <Button
          variant={running ? "danger" : "primary"}
          disabled={busy}
          onClick={() => onToggle(!running)}
          className="mt-auto"
        >
          {busy ? "Please wait…" : running ? "Stop" : "Start"}
        </Button>
      )}
    </section>
  );
}
