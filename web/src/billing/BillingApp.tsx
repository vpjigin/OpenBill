import { useEffect, useState } from "react";
import { Route, Routes } from "react-router-dom";
import { APP_NAME } from "../shared/brand";
import { LoginPage } from "../shared/LoginPage";
import { Shell } from "../shared/Shell";
import { useSession } from "../shared/useSession";
import { UserMenuFooter } from "../shared/UserMenuFooter";
import { NewBillPage } from "./pages/NewBillPage";
import { InvoicesPage } from "./pages/InvoicesPage";
import { InvoiceDetailPage } from "./pages/InvoiceDetailPage";
import { ItemsPage } from "./pages/ItemsPage";
import { LabelPrintPage } from "./pages/LabelPrintPage";
import { CustomersPage } from "./pages/CustomersPage";
import { CreditNotesPage } from "./pages/CreditNotesPage";
import { CreditNoteDetailPage } from "./pages/CreditNoteDetailPage";
import { CreateCreditNotePage } from "./pages/CreateCreditNotePage";
import { billingApi } from "./billingApi";
import { ReportsPage } from "./pages/ReportsPage";
import { SuppliersPage } from "./pages/SuppliersPage";
import { PurchasesPage } from "./pages/PurchasesPage";
import { NewPurchasePage } from "./pages/NewPurchasePage";
import { PurchaseDetailPage } from "./pages/PurchaseDetailPage";

const channelLabel = { ADMIN: "admin", LOCAL: "this computer", LAN: "Wi-Fi", PUBLIC: "public link" } as const;

export function BillingApp() {
  const { user, setUser, loading, logout } = useSession();

  if (loading) return null;
  if (!user) return <LoginPage title={APP_NAME} subtitle="Sign in to start billing" onLoggedIn={setUser} />;

  const canManage = user.role === "ADMIN" || user.role === "MANAGER";
  const [lowStockCount, setLowStockCount] = useState(0);

  useEffect(() => {
    if (!canManage) return;
    const load = () => billingApi.lowStockItems().then((r) => setLowStockCount(r.length)).catch(() => setLowStockCount(0));
    load();
    const t = setInterval(load, 60_000);
    return () => clearInterval(t);
  }, [canManage]);

  return (
    <Shell
      subtitle={`${APP_NAME} · web`}
      nav={[
        { to: "/", label: "New bill" },
        { to: "/invoices", label: "Invoices" },
        { to: "/customers", label: "Customers" },
        ...(canManage ? [{ to: "/items", label: "Items & stock", badge: lowStockCount }] : []),
        ...(canManage ? [{ to: "/credit-notes", label: "Credit notes" }] : []),
        ...(canManage ? [{ to: "/reports", label: "Reports" }] : []),
        ...(canManage ? [{ to: "/suppliers", label: "Suppliers" }] : []),
        ...(canManage ? [{ to: "/purchases", label: "Purchases" }] : []),
      ]}
      footer={
        <UserMenuFooter
          user={user}
          channelLabel={channelLabel[user.channel]}
          onLogout={() => void logout()}
        />
      }
    >
      <Routes>
        <Route path="/" element={<NewBillPage user={user} />} />
        <Route path="/invoices" element={<InvoicesPage />} />
        <Route path="/invoices/:id" element={<InvoiceDetailPage />} />
        {canManage && (
          <>
            <Route path="/invoices/:id/credit-note" element={<CreateCreditNotePage />} />
            <Route path="/credit-notes" element={<CreditNotesPage />} />
            <Route path="/credit-notes/:id" element={<CreditNoteDetailPage />} />
          </>
        )}
        <Route path="/customers" element={<CustomersPage />} />
        {canManage && <Route path="/items" element={<ItemsPage />} />}
        {canManage && <Route path="/items/labels" element={<LabelPrintPage />} />}
        {canManage && <Route path="/reports" element={<ReportsPage />} />}
        {canManage && <Route path="/suppliers" element={<SuppliersPage />} />}
        {canManage && <Route path="/purchases" element={<PurchasesPage />} />}
        {canManage && <Route path="/purchases/new" element={<NewPurchasePage />} />}
        {canManage && <Route path="/purchases/:id" element={<PurchaseDetailPage />} />}
      </Routes>
    </Shell>
  );
}
