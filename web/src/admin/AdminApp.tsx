import { useEffect, useState } from "react";
import { Route, Routes } from "react-router-dom";
import { APP_NAME_ADMIN } from "../shared/brand";
import { LoginPage } from "../shared/LoginPage";
import { Shell } from "../shared/Shell";
import { useSession } from "../shared/useSession";
import { UserMenuFooter } from "../shared/UserMenuFooter";
import { Button, errorText } from "../shared/ui";
import { adminApi } from "./adminApi";
import { SetupPage } from "./pages/SetupPage";
import { DashboardPage } from "./pages/DashboardPage";
import { AccessPage } from "./pages/AccessPage";
import { UsersPage } from "./pages/UsersPage";
import { SettingsPage } from "./pages/SettingsPage";
import { ActivityPage } from "./pages/ActivityPage";
import { BackupPage } from "./pages/BackupPage";
import { PrintersPage } from "./pages/PrintersPage";

export function AdminApp() {
  const [setupDone, setSetupDone] = useState<boolean | null>(null);
  const { user, setUser, loading, logout } = useSession();

  useEffect(() => {
    adminApi.setupStatus().then((s) => setSetupDone(s.setupDone)).catch(() => setSetupDone(true));
  }, []);

  if (setupDone === null || loading) return null;
  if (!setupDone) return <SetupPage onDone={() => setSetupDone(true)} />;
  if (!user) return <LoginPage title={APP_NAME_ADMIN} subtitle="Sign in with an admin account" onLoggedIn={setUser} />;

  return (
    <Shell
      subtitle="Admin · this computer"
      nav={[{ to: "/", label: "Dashboard" }]}
      sectionLabel="Administration"
      sectionNav={[
        { to: "/access", label: "Server & access" },
        { to: "/users", label: "Users & logins" },
        { to: "/settings", label: "Shop & GST settings" },
        { to: "/activity", label: "Activity log" },
        { to: "/backup", label: "Backup" },
        { to: "/printers", label: "Printers" },
      ]}
      footer={
        <div className="flex flex-col gap-2">
          <OpenBillingButton />
          <UserMenuFooter user={user} onLogout={() => void logout()} compact />
        </div>
      }
    >
      <Routes>
        <Route path="/" element={<DashboardPage />} />
        <Route path="/access" element={<AccessPage />} />
        <Route path="/users" element={<UsersPage currentUserId={user.userId} />} />
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/activity" element={<ActivityPage />} />
        <Route path="/backup" element={<BackupPage />} />
        <Route path="/printers" element={<PrintersPage />} />
      </Routes>
    </Shell>
  );
}

export function OpenBillingButton({ large = false }: { large?: boolean }) {
  const [msg, setMsg] = useState<string | null>(null);
  async function open() {
    setMsg(null);
    try {
      await adminApi.openBilling();
    } catch (e) {
      setMsg(errorText(e));
    }
  }
  return (
    <div className="flex flex-col gap-1">
      <Button
        variant="primary"
        onClick={open}
        className={large ? "min-h-13 px-6 text-base" : "bg-accent border-accent text-[#0e1714] hover:bg-accent/90"}
      >
        Open billing website
      </Button>
      {msg && <span className="text-xs text-danger">{msg}</span>}
    </div>
  );
}
