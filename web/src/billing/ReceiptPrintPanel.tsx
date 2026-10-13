import { useCallback, useEffect, useState } from "react";
import { Button, errorText } from "../shared/ui";
import { billingApi } from "./billingApi";
import { useReceiptPrintMode } from "./useReceiptPrintMode";

type PrintKind = "invoice" | "credit-note" | "day-end";

interface Props {
  kind: PrintKind;
  id?: string;
  dayEndDate?: string;
  autoPrint?: boolean;
}

export function ReceiptPrintPanel({ kind, id, dayEndDate, autoPrint }: Props) {
  const { mode, setMode } = useReceiptPrintMode();
  const [configured, setConfigured] = useState(false);
  const [status, setStatus] = useState<"idle" | "ok" | "error">("idle");
  const [error, setError] = useState<string | null>(null);
  const [busy, setBusy] = useState(false);
  const [expanded, setExpanded] = useState(false);

  const loadStatus = useCallback(() => {
    billingApi
      .printStatus()
      .then((s) => setConfigured(s.configured))
      .catch(() => setConfigured(false));
  }, []);

  useEffect(() => {
    loadStatus();
  }, [loadStatus]);

  const print = useCallback(async () => {
    if (!configured) return;
    setBusy(true);
    setError(null);
    try {
      if (kind === "invoice" && id) await billingApi.printInvoice(id);
      else if (kind === "credit-note" && id) await billingApi.printCreditNote(id);
      else if (kind === "day-end" && dayEndDate) await billingApi.printDayEnd(dayEndDate);
      setStatus("ok");
    } catch (e) {
      setStatus("error");
      setError(errorText(e));
    } finally {
      setBusy(false);
    }
  }, [configured, kind, id, dayEndDate]);

  useEffect(() => {
    if (autoPrint && mode === "server" && configured) {
      void print();
    }
  }, [autoPrint, mode, configured, print]);

  const modeLabel = mode === "server" ? "Server printer" : "Browser print";
  const printerLabel = configured ? "Printer ready" : "No printer";

  return (
    <div className="no-print rounded-xl border border-line bg-surface text-sm">
      <button
        type="button"
        className="flex min-h-11 w-full items-center justify-between gap-2 px-4 py-2 text-left md:hidden"
        onClick={() => setExpanded((v) => !v)}
        aria-expanded={expanded}
      >
        <span className="truncate text-muted">
          {printerLabel} · {modeLabel}
        </span>
        <span className="shrink-0 text-muted">{expanded ? "▴" : "▾"}</span>
      </button>

      <div className={`p-4 ${expanded ? "block" : "hidden md:block"}`}>
        <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
          <span className="font-medium">Receipt printer</span>
          <label className="flex min-h-11 flex-wrap items-center gap-2 text-base sm:text-[13px]">
            Print bills:
            <select
              value={mode}
              onChange={(e) => setMode(e.target.value as "server" | "browser")}
              className="min-h-11 rounded-lg border border-[#b9c2bd] px-2 text-base sm:text-sm"
            >
              <option value="server">Server printer</option>
              <option value="browser">Browser</option>
            </select>
          </label>
        </div>
        {mode === "server" && (
          <div className="flex flex-wrap items-center gap-2">
            {!configured ? (
              <span className="text-muted">No default printer — set one in Admin → Printers, or use browser print.</span>
            ) : (
              <>
                {status === "ok" && <span className="font-medium text-brand">Printed ✓</span>}
                {status === "error" && error && <span className="text-danger">{error}</span>}
                <Button className="min-h-11" disabled={busy} onClick={() => void print()}>
                  {busy ? "Printing…" : "Print receipt"}
                </Button>
              </>
            )}
          </div>
        )}
      </div>
    </div>
  );
}
