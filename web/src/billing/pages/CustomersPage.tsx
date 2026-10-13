import { useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";
import { GST_STATES, gstStateName, stateCodeFromGstin } from "../../shared/gstStates";
import { DataTable, SplitLayout } from "../../shared/layout";
import { Button, Card, ErrorNote, Field, ListPager, PageHeader, errorText } from "../../shared/ui";
import { billingApi, type Customer } from "../billingApi";

const PAGE_SIZE = 50;
const empty = { name: "", phone: "", gstin: "", stateCode: "", address: "", email: "", pincode: "" };

export function CustomersPage() {
  const [rows, setRows] = useState<Customer[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(0);
  const [q, setQ] = useState("");
  const [debouncedQ, setDebouncedQ] = useState("");
  const [form, setForm] = useState(empty);
  const [editing, setEditing] = useState<Customer | null>(null);
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
      .customers({ q: debouncedQ || undefined, page, size: PAGE_SIZE })
      .then((r) => {
        setRows(r.items);
        setTotal(r.total);
      })
      .catch((e) => setError(errorText(e)));
  }, [debouncedQ, page]);

  useEffect(() => {
    load();
  }, [load]);

  function edit(c: Customer) {
    setEditing(c);
    setForm({
      name: c.name,
      phone: c.phone ?? "",
      gstin: c.gstin ?? "",
      stateCode: c.stateCode ?? "",
      address: c.address ?? "",
      email: c.email ?? "",
      pincode: c.pincode ?? "",
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
      email: form.email,
      pincode: form.pincode || null,
      version: editing?.version ?? null,
    };
    try {
      if (editing) await billingApi.updateCustomer(editing.id, body);
      else await billingApi.createCustomer(body);
      setForm(empty);
      setEditing(null);
      load();
    } catch (err) {
      setError(errorText(err));
    }
  }

  const totalLabel =
    total === 1 ? "1 customer" : `${total.toLocaleString("en-IN")} customers`;

  const formPanel = (
    <form onSubmit={submit} className="flex flex-col gap-4">
      <Field label="Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
      <Field label="Phone" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} inputMode="tel" />
      <Field label="GSTIN" value={form.gstin} onChange={(e) => onGstinChange(e.target.value)} maxLength={15} />
      {!form.gstin && (
        <div className="flex flex-col gap-1.5">
          <label htmlFor="cust-state" className="text-[13px] font-medium">
            State
          </label>
          <select
            id="cust-state"
            value={form.stateCode}
            onChange={(e) => setForm({ ...form, stateCode: e.target.value })}
            className="min-h-11 rounded-lg border border-[#b9c2bd] bg-surface px-3 text-base sm:text-sm"
          >
            <option value="">—</option>
            {GST_STATES.map((s) => (
              <option key={s.code} value={s.code}>
                {s.code} — {s.name}
              </option>
            ))}
          </select>
        </div>
      )}
      <Field label="Address" value={form.address} onChange={(e) => setForm({ ...form, address: e.target.value })} />
      <Field
        label="Pincode"
        hint="Required for e-invoice export when this customer has a GSTIN."
        value={form.pincode}
        onChange={(e) => setForm({ ...form, pincode: e.target.value })}
        inputMode="numeric"
        pattern="[0-9]{6}"
        maxLength={6}
      />
      <Field label="Email" type="email" value={form.email} onChange={(e) => setForm({ ...form, email: e.target.value })} />
      <div className="flex gap-2">
        <Button type="submit" variant="primary" className="min-h-11 flex-1">
          {editing ? "Save changes" : "Add customer"}
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
      <PageHeader title="Customers" subtitle="Search by name, phone prefix, or full GSTIN." />
      <ErrorNote message={error} />
      <SplitLayout
        formTitle={editing ? "Edit customer" : "Add customer"}
        addLabel={editing ? "Edit customer" : "Add customer"}
        form={<div className="rounded-xl border border-line bg-surface p-5 xl:border-0 xl:p-0">{formPanel}</div>}
        list={
          <Card
            className="w-full min-w-0"
            actions={
              <div className="flex w-full min-w-0 flex-wrap items-center gap-3">
                <span className="text-[13px] text-muted">{totalLabel}</span>
                <input
                  type="search"
                  aria-label="Search customers"
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
                  { key: "name", header: "Name", mobilePrimary: true, cell: (c) => c.name },
                  { key: "phone", header: "Phone", cell: (c) => <span className="font-mono text-[13px]">{c.phone ?? "—"}</span> },
                  { key: "gstin", header: "GSTIN", cell: (c) => <span className="font-mono text-[13px]">{c.gstin ?? "—"}</span> },
                  {
                    key: "state",
                    header: "State",
                    cell: (c) =>
                      c.stateCode ? `${c.stateCode} · ${gstStateName(c.stateCode) ?? ""}` : "—",
                  },
                ]}
                rows={rows}
                rowKey={(c) => c.id}
                emptyMessage={debouncedQ ? "No matches." : "No customers yet — add one on the right."}
                rowActions={(c) => (
                  <Button className="min-h-11 px-3 text-[13px]" onClick={() => edit(c)}>
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
