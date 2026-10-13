import { useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";
import { GST_STATES, gstStateName, stateCodeFromGstin } from "../../shared/gstStates";
import { Button, Card, ErrorNote, Field, PageHeader, errorText } from "../../shared/ui";
import { billingApi, type Customer } from "../billingApi";

const empty = { name: "", phone: "", gstin: "", stateCode: "", address: "", email: "", pincode: "" };

export function CustomersPage() {
  const [rows, setRows] = useState<Customer[]>([]);
  const [q, setQ] = useState("");
  const [form, setForm] = useState(empty);
  const [editing, setEditing] = useState<Customer | null>(null);
  const [error, setError] = useState<string | null>(null);

  const load = useCallback(() => {
    billingApi.customers(q.trim() || undefined).then(setRows).catch((e) => setError(errorText(e)));
  }, [q]);
  useEffect(load, [load]);

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

  return (
    <>
      <PageHeader title="Customers" subtitle="Search by name, phone prefix, or full GSTIN." />
      <ErrorNote message={error} />
      <div className="flex flex-wrap items-start gap-5">
        <Card
          className="min-w-0 flex-[999_1_520px]"
          title="Customers"
          actions={
            <input
              type="search"
              aria-label="Search customers"
              placeholder="Search"
              value={q}
              onChange={(e) => setQ(e.target.value)}
              className="min-h-10 w-48 rounded-lg border border-[#b9c2bd] px-3 text-sm"
            />
          }
        >
          <div className="overflow-x-auto">
            <table className="w-full min-w-[600px] border-collapse text-sm">
              <thead>
                <tr className="text-left text-xs uppercase tracking-wider text-muted">
                  <th className="px-5 py-2.5 font-medium">Name</th>
                  <th className="px-3 py-2.5 font-medium">Phone</th>
                  <th className="px-3 py-2.5 font-medium">GSTIN</th>
                  <th className="px-3 py-2.5 font-medium">State</th>
                  <th className="px-5 py-2.5 text-right font-medium">Action</th>
                </tr>
              </thead>
              <tbody>
                {rows.map((c) => (
                  <tr key={c.id} className="border-t border-line-soft">
                    <td className="px-5 py-3 font-medium">{c.name}</td>
                    <td className="px-3 py-3 font-mono text-[13px]">{c.phone ?? "—"}</td>
                    <td className="px-3 py-3 font-mono text-[13px]">{c.gstin ?? "—"}</td>
                    <td className="px-3 py-3 text-[13px]">
                      {c.stateCode ? `${c.stateCode} · ${gstStateName(c.stateCode) ?? ""}` : "—"}
                    </td>
                    <td className="px-5 py-3 text-right">
                      <Button className="min-h-9 px-3 text-[13px]" onClick={() => edit(c)}>
                        Edit
                      </Button>
                    </td>
                  </tr>
                ))}
                {rows.length === 0 && (
                  <tr className="border-t border-line-soft">
                    <td colSpan={5} className="px-5 py-4 text-muted">
                      {q.trim() ? "No matches." : "Type in the search box to find customers."}
                    </td>
                  </tr>
                )}
              </tbody>
            </table>
          </div>
        </Card>

        <form onSubmit={submit} className="flex min-w-0 flex-[1_1_300px] flex-col gap-4 rounded-xl border border-line bg-surface p-5">
          <h2 className="m-0 text-[17px] font-semibold">{editing ? "Edit customer" : "Add customer"}</h2>
          <Field label="Name" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          <Field label="Phone" value={form.phone} onChange={(e) => setForm({ ...form, phone: e.target.value })} inputMode="tel" />
          <Field label="GSTIN" value={form.gstin} onChange={(e) => onGstinChange(e.target.value)} maxLength={15} />
          {!form.gstin && (
            <div className="flex flex-col gap-1.5">
              <label htmlFor="cust-state" className="text-[13px] font-medium">State</label>
              <select
                id="cust-state"
                value={form.stateCode}
                onChange={(e) => setForm({ ...form, stateCode: e.target.value })}
                className="min-h-11 rounded-lg border border-[#b9c2bd] bg-surface px-3 text-sm"
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
            <Button type="submit" variant="primary" className="flex-1">
              {editing ? "Save changes" : "Add customer"}
            </Button>
            {editing && (
              <Button type="button" onClick={() => { setEditing(null); setForm(empty); }}>
                Cancel
              </Button>
            )}
          </div>
        </form>
      </div>
    </>
  );
}
