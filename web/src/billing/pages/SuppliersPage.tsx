import { useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";
import { GST_STATES, stateCodeFromGstin } from "../../shared/gstStates";
import { DataTable, SplitLayout } from "../../shared/layout";
import { Button, Card, ErrorNote, Field, ListPager, PageHeader, errorText } from "../../shared/ui";
import { billingApi, type Supplier } from "../billingApi";

const PAGE_SIZE = 50;
const empty = { name: "", phone: "", gstin: "", stateCode: "", address: "" };

export function SuppliersPage() {
  const [rows, setRows] = useState<Supplier[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [q, setQ] = useState("");
  const [debouncedQ, setDebouncedQ] = useState("");
  const [form, setForm] = useState(empty);
  const [editing, setEditing] = useState<Supplier | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    const t = setTimeout(() => setDebouncedQ(q.trim()), 250);
    return () => clearTimeout(t);
  }, [q]);

  useEffect(() => {
    setPage(0);
  }, [debouncedQ]);

  const load = useCallback(() => {
    billingApi
      .suppliers({ q: debouncedQ || undefined, page, size: PAGE_SIZE })
      .then((r) => {
        setRows(r.items);
        setTotal(r.total);
      })
      .catch((e) => setError(errorText(e)));
  }, [debouncedQ, page]);

  useEffect(() => {
    load();
  }, [load]);

  function edit(s: Supplier) {
    setEditing(s);
    setForm({
      name: s.name,
      phone: s.phone ?? "",
      gstin: s.gstin ?? "",
      stateCode: s.stateCode ?? "",
      address: s.address ?? "",
    });
  }

  function onGstinChange(gstin: string) {
    const upper = gstin.toUpperCase();
    const fromGstin = stateCodeFromGstin(upper);
    setForm((f) => ({ ...f, gstin: upper, stateCode: fromGstin ?? f.stateCode }));
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    const body = {
      name: form.name,
      phone: form.phone,
      gstin: form.gstin || null,
      stateCode: form.gstin ? null : form.stateCode || null,
      address: form.address,
      version: editing?.version ?? null,
    };
    try {
      if (editing) await billingApi.updateSupplier(editing.id, body);
      else await billingApi.createSupplier(body);
      setForm(empty);
      setEditing(null);
      load();
    } catch (err) {
      setError(errorText(err));
    }
  }

  const totalLabel = total === 1 ? "1 supplier" : `${total.toLocaleString("en-IN")} suppliers`;

  const formPanel = (
    <form className="flex flex-col gap-3" onSubmit={(e) => void submit(e)}>
      <Field label="Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
      <Field label="Phone" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
      <Field label="GSTIN" value={form.gstin} onChange={(e) => onGstinChange(e.target.value)} maxLength={15} />
      {!form.gstin && (
        <label className="flex flex-col gap-1 text-sm">
          <span className="text-muted">State</span>
          <select
            className="min-h-11 rounded-lg border border-line px-2 text-base sm:text-sm"
            value={form.stateCode}
            onChange={(e) => setForm((f) => ({ ...f, stateCode: e.target.value }))}
          >
            <option value="">—</option>
            {GST_STATES.map((s) => (
              <option key={s.code} value={s.code}>
                {s.code} — {s.name}
              </option>
            ))}
          </select>
        </label>
      )}
      <Field label="Address" value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} />
      <div className="flex gap-2 pt-1">
        <Button type="submit" variant="primary" className="min-h-11 flex-1">
          {editing ? "Save" : "Add supplier"}
        </Button>
        {editing && (
          <Button type="button" className="min-h-11" onClick={() => { setEditing(null); setForm(empty); }}>
            Cancel
          </Button>
        )}
      </div>
    </form>
  );

  return (
    <>
      <PageHeader title="Suppliers" subtitle="Vendors you buy stock from." />
      <ErrorNote message={error} />
      <SplitLayout
        formTitle={editing ? "Edit supplier" : "New supplier"}
        addLabel={editing ? "Edit supplier" : "Add supplier"}
        form={<div className="rounded-xl border border-line bg-surface p-5 xl:border-0 xl:p-0">{formPanel}</div>}
        list={
          <Card
            className="w-full min-w-0"
            actions={
              <div className="flex w-full min-w-0 flex-wrap items-center gap-3">
                <span className="text-[13px] text-muted">{totalLabel}</span>
                <input
                  type="search"
                  aria-label="Search suppliers"
                  placeholder="Search"
                  value={q}
                  onChange={(e) => setQ(e.target.value)}
                  className="min-h-11 min-w-0 flex-1 rounded-lg border border-[#b9c2bd] px-3 text-base sm:max-w-xs sm:text-sm"
                />
              </div>
            }
          >
            <div className="px-2 pb-2 sm:px-4">
              <DataTable
                columns={[
                  { key: "name", header: "Name", mobilePrimary: true, cell: (s) => s.name },
                  { key: "gst", header: "GSTIN / phone", cell: (s) => s.gstin ?? s.phone ?? "—" },
                ]}
                rows={rows}
                rowKey={(s) => s.id}
                emptyMessage={debouncedQ ? "No matches." : "No suppliers yet — add one on the right."}
                rowActions={(s) => (
                  <Button variant="secondary" className="min-h-11 px-3 text-[13px]" onClick={() => edit(s)}>
                    Edit
                  </Button>
                )}
              />
            </div>
            <ListPager page={page} size={PAGE_SIZE} total={total} onPage={setPage} />
          </Card>
        }
      />
    </>
  );
}
