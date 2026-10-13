import { useState } from "react";
import type { FormEvent } from "react";
import { Button, ErrorNote, Field, errorText } from "../../shared/ui";
import { adminApi } from "../adminApi";

/** First run: shop details + the owner's admin account. */
export function SetupPage({ onDone }: { onDone: () => void }) {
  const [f, setF] = useState({ shopName: "", gstin: "", stateCode: "", adminName: "", username: "", password: "" });
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await adminApi.setup(f);
      onDone();
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex min-h-full items-center justify-center p-4">
      <form onSubmit={submit} className="flex w-full max-w-lg flex-col gap-4 rounded-xl border border-line bg-surface p-6">
        <div>
          <h1 className="m-0 text-2xl font-semibold">Welcome — let's set up your shop</h1>
          <p className="m-0 mt-1 text-sm text-muted">Everything stays on this computer. You can change these later.</p>
        </div>
        <Field label="Shop name" value={f.shopName} onChange={set("shopName")} required autoFocus />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field label="GSTIN (optional)" value={f.gstin} onChange={set("gstin")} maxLength={15} />
          <Field label="State code (optional)" hint="e.g. 32 for Kerala" value={f.stateCode} onChange={set("stateCode")} maxLength={2} />
        </div>
        <hr className="m-0 border-line-soft" />
        <p className="m-0 text-sm font-medium">Owner (admin) account</p>
        <Field label="Your name" value={f.adminName} onChange={set("adminName")} required />
        <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
          <Field label="Username" value={f.username} onChange={set("username")} required autoComplete="username" />
          <Field label="Password" type="password" value={f.password} onChange={set("password")} required minLength={4} autoComplete="new-password" />
        </div>
        <ErrorNote message={error} />
        <Button type="submit" variant="primary" disabled={busy}>
          {busy ? "Setting up…" : "Finish setup"}
        </Button>
      </form>
    </div>
  );
}
