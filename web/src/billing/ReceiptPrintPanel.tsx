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

  return (
    <div className="no-print rounded-xl border border-line bg-surface p-4 text-sm">
      <div className="mb-3 flex flex-wrap items-center justify-between gap-2">
        <span className="font-medium">Receipt printer</span>
        <label className="flex items-center gap-2 text-[13px]">
          Print bills:
          <select
            value={mode}
            onChange={(e) => setMode(e.target.value as "server" | "browser")}
            className="rounded-lg border border-[#b9c2bd] px-2 py-1"
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
              {status === "ok" && <span className="text-brand font-medium">Printed ✓</span>}
              {status === "error" && error && <span className="text-danger">{error}</span>}
              <Button disabled={busy} onClick={() => void print()}>
                {busy ? "Printing…" : status === "error" ? "Retry" : "Print on receipt printer"}
              </Button>
              <Button onClick={() => setMode("browser")}>Print in browser</Button>
            </>
          )}
        </div>
      )}
      {mode === "browser" && (
        <p className="m-0 text-muted">Use the browser print section below (Ctrl+P).</p>
      )}
    </div>
  );
}
