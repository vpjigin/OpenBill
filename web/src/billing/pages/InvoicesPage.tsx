import { useCallback, useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { DataTable, Toolbar, ToolbarField } from "../../shared/layout";
import { formatPaise } from "../../shared/money";
import { Card, PageHeader } from "../../shared/ui";
import { billingApi, type Invoice } from "../billingApi";

function todayIso(): string {
  return new Date().toISOString().slice(0, 10);
}

function monthStartIso(): string {
  const d = new Date();
  return new Date(d.getFullYear(), d.getMonth(), 1).toISOString().slice(0, 10);
}

export function InvoicesPage() {
  const navigate = useNavigate();
  const [rows, setRows] = useState<Invoice[]>([]);
  const [from, setFrom] = useState(monthStartIso());
  const [to, setTo] = useState(todayIso());
  const [q, setQ] = useState("");

  const load = useCallback(() => {
    billingApi
      .invoices({ from: from || undefined, to: to || undefined, q: q.trim() || undefined, limit: 100 })
      .then(setRows)
      .catch(() => setRows([]));
  }, [from, to, q]);

  useEffect(() => {
    const t = setTimeout(load, 200);
    return () => clearTimeout(t);
  }, [load]);

  return (
    <>
      <PageHeader title="Invoices" subtitle="Search and reprint saved bills" />
      <Card
        actions={
          <Toolbar>
            <ToolbarField label="From">
              <input
                type="date"
                aria-label="From date"
                value={from}
                onChange={(e) => setFrom(e.target.value)}
                className="rounded-lg border border-[#b9c2bd] px-2"
              />
            </ToolbarField>
            <ToolbarField label="To">
              <input
                type="date"
                aria-label="To date"
                value={to}
                onChange={(e) => setTo(e.target.value)}
                className="rounded-lg border border-[#b9c2bd] px-2"
              />
            </ToolbarField>
            <ToolbarField label="Search">
              <input
                type="search"
                aria-label="Search invoices"
                placeholder="Invoice no, customer…"
                value={q}
                onChange={(e) => setQ(e.target.value)}
                className="rounded-lg border border-[#b9c2bd] px-3"
              />
            </ToolbarField>
          </Toolbar>
        }
      >
        <div className="px-2 pb-4 sm:px-4">
          <DataTable
            columns={[
              {
                key: "no",
                header: "Invoice",
                mobilePrimary: true,
                cell: (i) => <span className="font-mono text-[13px]">{i.invoiceNo}</span>,
              },
              {
                key: "date",
                header: "Date",
                cell: (i) =>
                  new Date(i.invoiceDate).toLocaleString("en-IN", { dateStyle: "medium", timeStyle: "short" }),
              },
              { key: "customer", header: "Customer", cell: (i) => i.customerName ?? "Walk-in" },
              { key: "payment", header: "Payment", hideOnMobile: true, cell: (i) => i.paymentMode },
              {
                key: "total",
                header: "Total",
                mobileAmount: true,
                className: "text-right font-mono",
                cell: (i) => formatPaise(i.totalPaise),
              },
            ]}
            rows={rows}
            rowKey={(i) => i.id}
            onRowClick={(i) => navigate(`/invoices/${i.id}`)}
            emptyMessage="No invoices in this range."
          />
        </div>
      </Card>
    </>
  );
}
