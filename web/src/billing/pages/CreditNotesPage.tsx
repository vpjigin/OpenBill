import { useEffect, useState } from "react";
import { useNavigate } from "react-router-dom";
import { DataTable, Toolbar, ToolbarField } from "../../shared/layout";
import { formatPaise } from "../../shared/money";
import { Card, ErrorNote, PageHeader, errorText } from "../../shared/ui";
import { billingApi, type CreditNote } from "../billingApi";

function todayIso() {
  return new Date().toISOString().slice(0, 10);
}

export function CreditNotesPage() {
  const navigate = useNavigate();
  const [from, setFrom] = useState(() => {
    const d = new Date();
    d.setDate(d.getDate() - 30);
    return d.toISOString().slice(0, 10);
  });
  const [to, setTo] = useState(todayIso);
  const [rows, setRows] = useState<CreditNote[]>([]);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    billingApi
      .creditNotes({ from, to, limit: 200 })
      .then(setRows)
      .catch((e) => setError(errorText(e)));
  }, [from, to]);

  return (
    <>
      <PageHeader title="Credit notes" subtitle="Returns and refunds" />
      <ErrorNote message={error} />
      <Card
        actions={
          <Toolbar>
            <ToolbarField label="From">
              <input type="date" className="rounded-lg border border-line px-2" value={from} onChange={(e) => setFrom(e.target.value)} />
            </ToolbarField>
            <ToolbarField label="To">
              <input type="date" className="rounded-lg border border-line px-2" value={to} onChange={(e) => setTo(e.target.value)} />
            </ToolbarField>
          </Toolbar>
        }
      >
        <div className="px-2 pb-4 sm:px-4">
          <DataTable
            columns={[
              { key: "cn", header: "Credit note", mobilePrimary: true, cell: (cn) => cn.cnNo },
              {
                key: "date",
                header: "Date",
                cell: (cn) => new Date(cn.cnDate).toLocaleString("en-IN"),
              },
              { key: "inv", header: "Invoice", cell: (cn) => cn.invoiceNo },
              {
                key: "total",
                header: "Total",
                mobileAmount: true,
                className: "text-right font-mono",
                cell: (cn) => formatPaise(cn.totalPaise),
              },
            ]}
            rows={rows}
            rowKey={(cn) => cn.id}
            onRowClick={(cn) => navigate(`/credit-notes/${cn.id}`)}
            emptyMessage="No credit notes in this range"
          />
        </div>
      </Card>
    </>
  );
}
