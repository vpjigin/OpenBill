import { useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";
import { adminApi, type PrinterConfig, type PrinterKind } from "../adminApi";
import { Button, Card, ErrorNote, Field, PageHeader, errorText } from "../../shared/ui";

const emptyForm = {
  name: "",
  kind: "SYSTEM" as PrinterKind,
  systemName: "",
  host: "",
  port: "9100",
  paperMm: 80,
  charsPerLine: "",
  openDrawer: false,
  cut: true,
};

export function PrintersPage() {
  const [osNames, setOsNames] = useState<string[]>([]);
  const [saved, setSaved] = useState<PrinterConfig[]>([]);
  const [form, setForm] = useState(emptyForm);
  const [editing, setEditing] = useState<PrinterConfig | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [msg, setMsg] = useState<string | null>(null);

  const load = useCallback(() => {
    Promise.all([adminApi.osPrinters(), adminApi.printers()])
      .then(([os, list]) => {
        setOsNames(os);
        setSaved(list);
      })
      .catch((e) => setError(errorText(e)));
  }, []);

  useEffect(load, [load]);

  function edit(p: PrinterConfig) {
    setEditing(p);
    setForm({
      name: p.name,
      kind: p.kind,
      systemName: p.systemName ?? "",
      host: p.host ?? "",
      port: String(p.port),
      paperMm: p.paperMm,
      charsPerLine: p.charsPerLine != null ? String(p.charsPerLine) : "",
      openDrawer: p.openDrawer,
      cut: p.cut,
    });
  }

  async function submit(e: FormEvent) {
    e.preventDefault();
    setError(null);
    setMsg(null);
    const body = {
      name: form.name,
      kind: form.kind,
      systemName: form.kind === "SYSTEM" ? form.systemName : null,
      host: form.kind === "NETWORK" ? form.host : null,
      port: form.kind === "NETWORK" ? Number(form.port) || 9100 : null,
      paperMm: form.paperMm,
      charsPerLine: form.charsPerLine.trim() ? Number(form.charsPerLine) : null,
      openDrawer: form.openDrawer,
      cut: form.cut,
      version: editing?.version ?? null,
    };
    try {
      if (editing) await adminApi.updatePrinter(editing.id, body);
      else await adminApi.createPrinter(body);
      setForm(emptyForm);
      setEditing(null);
      load();
    } catch (err) {
      setError(errorText(err));
    }
  }

  async function testPrint(id: string) {
    setMsg(null);
    setError(null);
    try {
      await adminApi.testPrinter(id);
      setMsg("Test print sent.");
    } catch (e) {
      setError(errorText(e));
    }
  }

  return (
    <>
      <PageHeader title="Receipt printers" subtitle="Thermal ESC/POS on this PC (USB or network RAW port 9100)." />
      <ErrorNote message={error} />
      {msg && <p className="m-0 text-sm text-brand">{msg}</p>}
      <div className="flex flex-wrap items-start gap-5">
        <Card className="min-w-0 flex-[999_1_520px]" title="Saved printers">
          <ul className="m-0 list-none space-y-3 p-0">
            {saved.map((p) => (
              <li key={p.id} className="rounded-lg border border-line-soft p-4 text-sm">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <span className="font-semibold">{p.name}</span>
                    {p.defaultPrinter && (
                      <span className="ml-2 rounded bg-brand-soft px-2 py-0.5 text-[11px] text-brand">Default</span>
                    )}
                    <p className="m-0 mt-1 text-muted">
                      {p.kind === "SYSTEM" ? p.systemName : `${p.host}:${p.port}`} · {p.paperMm}mm
                      {p.openDrawer ? " · drawer" : ""}
                    </p>
                  </div>
                  <div className="flex flex-wrap gap-1">
                    {!p.defaultPrinter && (
                      <Button className="min-h-9 px-2 text-[12px]" onClick={() => void adminApi.setDefaultPrinter(p.id).then(load)}>
                        Set default
                      </Button>
                    )}
                    <Button className="min-h-9 px-2 text-[12px]" onClick={() => void testPrint(p.id)}>
                      Test print
                    </Button>
                    <Button className="min-h-9 px-2 text-[12px]" onClick={() => edit(p)}>
                      Edit
                    </Button>
                    <Button
                      className="min-h-9 px-2 text-[12px]"
                      onClick={() => void adminApi.deletePrinter(p.id).then(load)}
                    >
                      Delete
                    </Button>
                  </div>
                </div>
              </li>
            ))}
            {saved.length === 0 && <li className="text-muted">No printers yet. Add one on the right.</li>}
          </ul>
        </Card>

        <form onSubmit={submit} className="flex min-w-0 flex-[1_1_300px] flex-col gap-4 rounded-xl border border-line bg-surface p-5">
          <h2 className="m-0 text-[17px] font-semibold">{editing ? "Edit printer" : "Add printer"}</h2>
          <Field label="Label" value={form.name} onChange={(e) => setForm({ ...form, name: e.target.value })} required />
          <div className="flex flex-col gap-1.5">
            <label className="text-[13px] font-medium">Connection</label>
            <select
              value={form.kind}
              onChange={(e) => setForm({ ...form, kind: e.target.value as PrinterKind })}
              className="min-h-11 rounded-lg border border-[#b9c2bd] px-3 text-sm"
            >
              <option value="SYSTEM">OS printer (USB / installed driver)</option>
              <option value="NETWORK">Network (RAW TCP)</option>
            </select>
          </div>
          {form.kind === "SYSTEM" ? (
            <div className="flex flex-col gap-1.5">
              <label className="text-[13px] font-medium">OS printer name</label>
              <select
                value={form.systemName}
                onChange={(e) => setForm({ ...form, systemName: e.target.value })}
                className="min-h-11 rounded-lg border border-[#b9c2bd] px-3 text-sm"
                required
              >
                <option value="">Select…</option>
                {osNames.map((n) => (
                  <option key={n} value={n}>
                    {n}
                  </option>
                ))}
              </select>
            </div>
          ) : (
            <div className="grid grid-cols-3 gap-2">
              <Field
                className="col-span-2"
                label="Host / IP"
                value={form.host}
                onChange={(e) => setForm({ ...form, host: e.target.value })}
                required
              />
              <Field label="Port" value={form.port} onChange={(e) => setForm({ ...form, port: e.target.value })} />
            </div>
          )}
          <div className="flex flex-col gap-1.5">
            <label className="text-[13px] font-medium">Paper width</label>
            <select
              value={form.paperMm}
              onChange={(e) => setForm({ ...form, paperMm: Number(e.target.value) })}
              className="min-h-11 rounded-lg border border-[#b9c2bd] px-3 text-sm"
            >
              <option value={80}>80 mm (48 columns)</option>
              <option value={58}>58 mm (32 columns)</option>
            </select>
          </div>
          <Field
            label="Chars per line (optional override)"
            value={form.charsPerLine}
            onChange={(e) => setForm({ ...form, charsPerLine: e.target.value })}
          />
          <label className="flex cursor-pointer items-center gap-2 text-sm">
            <input type="checkbox" checked={form.cut} onChange={(e) => setForm({ ...form, cut: e.target.checked })} />
            Cut paper after print
          </label>
          <label className="flex cursor-pointer items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={form.openDrawer}
              onChange={(e) => setForm({ ...form, openDrawer: e.target.checked })}
            />
            Open cash drawer on cash sales
          </label>
          <div className="flex gap-2">
            <Button type="submit" variant="primary" className="flex-1">
              {editing ? "Save" : "Add printer"}
            </Button>
            {editing && (
              <Button
                onClick={() => {
                  setEditing(null);
                  setForm(emptyForm);
                }}
              >
                Cancel
              </Button>
            )}
          </div>
        </form>
      </div>
    </>
  );
}
