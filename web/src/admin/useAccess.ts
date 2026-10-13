import { useCallback, useEffect, useState } from "react";
import { errorText } from "../shared/ui";
import { adminApi, type AccessChannel, type AccessStatus } from "./adminApi";

/** Access status, polled faster while the public tunnel is connecting. */
export function useAccess() {
  const [status, setStatus] = useState<AccessStatus | null>(null);
  const [busy, setBusy] = useState<AccessChannel | null>(null);
  const [error, setError] = useState<string | null>(null);

  const refresh = useCallback(() => {
    adminApi.access().then(setStatus).catch((e) => setError(errorText(e)));
  }, []);

  useEffect(() => {
    refresh();
    const connecting = status?.pub.state === "CONNECTING" || status?.pub.state === "RECONNECTING";
    const t = setInterval(refresh, connecting ? 1500 : 5000);
    return () => clearInterval(t);
  }, [refresh, status?.pub.state]);

  const toggle = useCallback(async (c: AccessChannel, run: boolean) => {
    setBusy(c);
    setError(null);
    try {
      setStatus(run ? await adminApi.startAccess(c) : await adminApi.stopAccess(c));
    } catch (e) {
      setError(errorText(e));
    } finally {
      setBusy(null);
    }
  }, []);

  return { status, busy, error, toggle };
}
