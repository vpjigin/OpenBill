import { useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";
import { GST_STATES, stateCodeFromGstin } from "../../shared/gstStates";
import { Button, Card, ErrorNote, Field, PageHeader, errorText } from "../../shared/ui";
import { billingApi, type Supplier } from "../billingApi";

const empty = { name: "", phone: "", gstin: "", stateCode: "", address: "" };

export function SuppliersPage() {
  const [rows, setRows] = useState<Supplier[]>([]);
  const [q, setQ] = useState("");
  const [form, setForm] = useState(empty);
  const [editing, setEditing] = useState<Supplier | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    billingApi.suppliers(q.trim() || undefined).then(setRows).catch((e) => setError(errorText(e)));
  }, [q]);
  useEffect(load, [load]);

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

  return (
    <>
      <PageHeader title="Suppliers" subtitle="Vendors you buy stock from." />
      <ErrorNote message={error} />
      <div className="flex flex-wrap items-start gap-5">
        <Card
          className="min-w-0 flex-[999_1_520px]"
          title="Suppliers"
          actions={
            <input
              type="search"
              aria-label="Search suppliers"
              placeholder="Search"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              className="min-h-10 w-48 rounded-lg border border-[#b9c2bd] px-3 text-sm"
            />
          }
        >
          {q.trim() === "" ? (
            <p className="text-sm text-muted">Type to search suppliers.</p>
          ) : rows.length === 0 ? (
            <p className="text-sm text-muted">No matches</p>
          ) : (
            <ul className="m-0 list-none divide-y divide-line-soft">
              {rows.map((s) => (
                <li key={s.id} className="flex items-center justify-between gap-2 py-2">
                  <div>
                    <div className="font-medium">{s.name}</div>
                    <div className="text-xs text-muted">{s.gstin ?? s.phone ?? "—"}</div>
                  </div>
                  <Button variant="secondary" className="min-h-9 px-3 text-[13px]" onClick={() => edit(s)}>
                    Edit
                  </Button>
                </li>
              ))}
            </ul>
          )}
        </Card>
        <Card className="min-w-[280px] flex-[1_1_320px]" title={editing ? "Edit supplier" : "New supplier"}>
          <form className="flex flex-col gap-3" onSubmit={(e) => void submit(e)}>
            <Field label="Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
            <Field label="Phone" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} />
            <Field label="GSTIN" value={form.gstin} onChange={(e) => onGstinChange(e.target.value)} maxLength={15} />
            {!form.gstin && (
              <label className="flex flex-col gap-1 text-sm">
                <span className="text-muted">State</span>
                <select
                  className="rounded-lg border border-line px-2 py-2"
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
            <div className="flex gap-2">
              <Button type="submit">{editing ? "Save" : "Add supplier"}</Button>
              {editing && (
                <Button
                  type="button"
                  variant="secondary"
                  onClick={() => {
                    setEditing(null);
                    setForm(empty);
                  }}
                >
                  Cancel
                </Button>
              )}
            </div>
          </form>
        </Card>
      </div>
    </>
  );
}
