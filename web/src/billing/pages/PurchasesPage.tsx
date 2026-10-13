import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { DataTable, Toolbar, ToolbarField } from "../../shared/layout";
import { formatPaise } from "../../shared/money";
import { Button, Card, ErrorNote, PageHeader, errorText } from "../../shared/ui";
import { billingApi, type PurchaseSummary } from "../billingApi";

function defaultRange() {
  const to = new Date();
  const from = new Date();
  from.setMonth(from.getMonth() - 1);
  return { from: from.toISOString().slice(0, 10), to: to.toISOString().slice(0, 10) };
}

export function PurchasesPage() {
  const navigate = useNavigate();
  const [rows, setRows] = useState<PurchaseSummary[]>([]);
  const [range, setRange] = useState(defaultRange);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    billingApi
      .purchases({ from: range.from, to: range.to })
      .then(setRows)
      .catch((e) => setError(errorText(e)));
  }, [range.from, range.to]);

  useEffect(() => {
    load();
  }, [load]);

  return (
    <>
      <PageHeader
        title="Purchases"
        subtitle="Stock in from supplier bills"
        actions={
          <Link to="/purchases/new">
            <Button className="min-h-11">New purchase</Button>
          </Link>
        }
      />
      <ErrorNote message={error} />
      <Card
        actions={
          <Toolbar>
            <ToolbarField label="From">
              <input
                type="date"
                className="rounded-lg border border-line px-2"
                value={range.from}
                onChange={(e) => setRange((r) => ({ ...r, from: e.target.value }))}
              />
            </ToolbarField>
            <ToolbarField label="To">
              <input
                type="date"
                className="rounded-lg border border-line px-2"
                value={range.to}
                onChange={(e) => setRange((r) => ({ ...r, to: e.target.value }))}
              />
            </ToolbarField>
            <Button variant="secondary" className="min-h-11" onClick={load}>
              Refresh
            </Button>
          </Toolbar>
        }
      >
        <div className="px-2 pb-4 sm:px-4">
          <DataTable
            columns={[
              { key: "bill", header: "Bill no", mobilePrimary: true, cell: (p) => <span className="font-mono">{p.billNo}</span> },
              { key: "date", header: "Date", cell: (p) => p.billDate },
              { key: "supplier", header: "Supplier", cell: (p) => p.supplierName },
              {
                key: "total",
                header: "Total",
                mobileAmount: true,
                className: "text-right font-mono",
                cell: (p) => formatPaise(p.totalPaise),
              },
            ]}
            rows={rows}
            rowKey={(p) => p.id}
            onRowClick={(p) => navigate(`/purchases/${p.id}`)}
            emptyMessage="No purchases in this range"
          />
        </div>
      </Card>
    </>
  );
}
