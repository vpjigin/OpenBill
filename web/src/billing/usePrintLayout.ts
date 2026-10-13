import { useCallback, useState } from "react";

export type PrintLayout = "80mm" | "a4";

const KEY = "billing-print-layout";

function readLayout(): PrintLayout {
  try {
    return localStorage.getItem(KEY) === "a4" ? "a4" : "80mm";
  } catch {
    return "80mm";
  }
}

export function usePrintLayout() {
  const [layout, setLayoutState] = useState<PrintLayout>(readLayout);

  const setLayout = useCallback((next: PrintLayout) => {
    setLayoutState(next);
    try {
      localStorage.setItem(KEY, next);
    } catch {
      /* private mode */
    }
  }, []);

  return { layout, setLayout };
}
