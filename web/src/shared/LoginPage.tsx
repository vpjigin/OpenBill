import { useState } from "react";
import type { FormEvent } from "react";
import { login, type CurrentUser } from "./api";
import { Button, ErrorNote, Field, errorText } from "./ui";

export function LoginPage({ title, subtitle, onLoggedIn }: {
  title: string;
  subtitle: string;
  onLoggedIn: (u: CurrentUser) => void;
}) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      onLoggedIn(await login(username, password));
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="flex min-h-full items-center justify-center p-4">
      <form onSubmit={submit} className="flex w-full max-w-sm flex-col gap-4 rounded-xl border border-line bg-surface p-6">
        <div className="flex items-center gap-3">
          <img src="/favicon.svg" alt="" className="size-10" />
          <div>
            <h1 className="m-0 text-xl font-semibold">{title}</h1>
            <p className="m-0 text-sm text-muted">{subtitle}</p>
          </div>
        </div>
        <Field label="Username" autoComplete="username" value={username} onChange={(e) => setUsername(e.target.value)} required autoFocus />
        <Field label="Password or PIN" type="password" autoComplete="current-password" value={password} onChange={(e) => setPassword(e.target.value)} required />
        <ErrorNote message={error} />
        <Button type="submit" variant="primary" disabled={busy}>
          {busy ? "Signing in…" : "Sign in"}
        </Button>
      </form>
    </div>
  );
}
