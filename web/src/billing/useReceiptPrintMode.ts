import { useCallback, useState } from "react";

export type ReceiptPrintMode = "server" | "browser";

const KEY = "billing-receipt-print";

function readMode(): ReceiptPrintMode {
  try {
    return localStorage.getItem(KEY) === "browser" ? "browser" : "server";
  } catch {
    return "server";
  }
}

export function useReceiptPrintMode() {
  const [mode, setModeState] = useState<ReceiptPrintMode>(readMode);

  const setMode = useCallback((next: ReceiptPrintMode) => {
    setModeState(next);
    try {
      localStorage.setItem(KEY, next);
    } catch {
      /* private mode */
    }
  }, []);

  return { mode, setMode };
}
