import { useState } from "react";
import type { FormEvent } from "react";
import { changePassword } from "./api";
import { Button, ErrorNote, Field, errorText } from "./ui";

export function ChangePasswordDialog({ onClose }: { onClose: () => void }) {
  const [current, setCurrent] = useState("");
  const [next, setNext] = useState("");
  const [confirm, setConfirm] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (next !== confirm) {
      setError("New password and confirmation do not match");
      return;
    }
    if (next.length < 4) {
      setError("Password or PIN must be at least 4 characters");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await changePassword(current, next);
      setDone(true);
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-4" role="dialog" aria-label="Change password">
      <form
        onSubmit={submit}
        className="w-full max-w-md rounded-xl border border-line bg-surface p-5 shadow-lg"
      >
        <h2 className="m-0 mb-4 text-lg font-semibold">Change password</h2>
        {done ? (
          <>
            <p className="m-0 text-sm text-muted">
              Password updated. Other devices signed in as you have been signed out.
            </p>
            <Button type="button" variant="primary" className="mt-4 w-full" onClick={onClose}>
              Done
            </Button>
          </>
        ) : (
          <>
            <div className="flex flex-col gap-3">
              <Field
                label="Current password"
                type="password"
                value={current}
                onChange={(e) => setCurrent(e.target.value)}
                required
                autoComplete="current-password"
              />
              <Field
                label="New password"
                type="password"
                value={next}
                onChange={(e) => setNext(e.target.value)}
                required
                minLength={4}
                autoComplete="new-password"
              />
              <Field
                label="Confirm new password"
                type="password"
                value={confirm}
                onChange={(e) => setConfirm(e.target.value)}
                required
                minLength={4}
                autoComplete="new-password"
              />
            </div>
            <ErrorNote message={error} />
            <div className="mt-4 flex gap-2">
              <Button type="button" className="flex-1" onClick={onClose} disabled={busy}>
                Cancel
              </Button>
              <Button type="submit" variant="primary" className="flex-1" disabled={busy}>
                {busy ? "Saving…" : "Update password"}
              </Button>
            </div>
          </>
        )}
      </form>
    </div>
  );
}
