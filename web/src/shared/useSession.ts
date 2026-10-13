import { useCallback, useEffect, useState } from "react";
import { logout as apiLogout, me, onUnauthorized, type CurrentUser } from "./api";

/** Loads the signed-in user once, and drops to the login screen on any 401. */
export function useSession() {
  const [user, setUser] = useState<CurrentUser | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    me().then((u) => {
      setUser(u);
      setLoading(false);
    });
    const off = onUnauthorized(() => setUser(null));
    return () => {
      off();
    };
  }, []);

  const logout = useCallback(async () => {
    await apiLogout();
    setUser(null);
  }, []);

  return { user, setUser, loading, logout };
}
