import { useCallback, useEffect, useState } from "react";
import type { FormEvent } from "react";
import type { Role } from "../../shared/api";
import { DataTable, SplitLayout } from "../../shared/layout";
import { Badge, Button, Card, ErrorNote, Field, PageHeader, errorText } from "../../shared/ui";
import { adminApi, type UserRow } from "../adminApi";

const roleHints: Record<Role, string> = {
  CASHIER: "Make bills and print. Cannot edit items or see reports.",
  MANAGER: "Cashier rights plus items, stock and reports.",
  ADMIN: "Everything, including users, server access and settings. Keep this to the owner.",
};

export function UsersPage({ currentUserId }: { currentUserId: string }) {
  const [users, setUsers] = useState<UserRow[]>([]);
  const [error, setError] = useState<string | null>(null);
  const load = useCallback(() => {
    adminApi.users().then(setUsers).catch((e) => setError(errorText(e)));
  }, []);
  useEffect(load, [load]);

  async function update(u: UserRow, patch: Partial<UserRow>) {
    setError(null);
    try {
      const next = { ...u, ...patch };
      await adminApi.updateUser(u.id, {
        name: next.name,
        role: next.role,
        webAccess: next.webAccess,
        active: next.active,
        newPassword: null,
      });
      load();
    } catch (e) {
      setError(errorText(e));
    }
  }

  const addForm = (
    <div className="rounded-xl border border-line bg-surface p-5 xl:border-0 xl:p-0">
      <AddUser onCreated={load} />
    </div>
  );

  return (
    <>
      <PageHeader
        title="Users & logins"
        subtitle="Only the admin on this computer can create users. Others sign in on the billing website."
      />
      <ErrorNote message={error} />
      <SplitLayout formTitle="Add user" addLabel="Add user" form={addForm} list={
        <Card
          className="w-full min-w-0"
          actions={<span className="text-[13px] text-muted">{users.length} users</span>}
        >
          <div className="px-2 pb-2 sm:px-4">
            <DataTable
              columns={[
                { key: "name", header: "Name", mobilePrimary: true, cell: (u) => u.name },
                { key: "user", header: "Username", cell: (u) => <span className="font-mono text-[13px]">{u.username}</span> },
                {
                  key: "role",
                  header: "Role",
                  cell: (u) => <Badge tone={u.role === "ADMIN" ? "warn" : "ok"}>{u.role.toLowerCase()}</Badge>,
                },
                {
                  key: "web",
                  header: "Web sign-in",
                  hideOnMobile: true,
                  cell: (u) => (
                    <label className="inline-flex min-h-11 cursor-pointer items-center gap-2">
                      <input
                        type="checkbox"
                        checked={u.webAccess}
                        onChange={(e) => update(u, { webAccess: e.target.checked })}
                        className="size-4 accent-brand"
                        onClick={(e) => e.stopPropagation()}
                      />
                      {u.webAccess ? "Allowed" : "Off"}
                    </label>
                  ),
                },
              ]}
              rows={users}
              rowKey={(u) => u.id}
              emptyMessage="No users yet."
              rowActions={(u) => (
                <div className="flex flex-col items-end gap-1 sm:flex-row sm:flex-wrap sm:justify-end">
                  <ResetPasswordButton user={u} onDone={load} />
                  {u.id === currentUserId ? (
                    <span className="text-[13px] text-muted">You</span>
                  ) : (
                    <Button
                      className="min-h-11 px-3 text-[13px]"
                      onClick={() => void update(u, { active: !u.active })}
                    >
                      {u.active ? "Disable" : "Enable"}
                    </Button>
                  )}
                </div>
              )}
            />
          </div>
        </Card>
      } />
    </>
  );
}

function ResetPasswordButton({ user, onDone }: { user: UserRow; onDone: () => void }) {
  const [open, setOpen] = useState(false);
  const [password, setPassword] = useState("");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [done, setDone] = useState(false);

  async function submit(e: FormEvent) {
    e.preventDefault();
    if (password.length < 4) {
      setError("Password or PIN must be at least 4 characters");
      return;
    }
    setBusy(true);
    setError(null);
    try {
      await adminApi.resetUserPassword(user.id, password);
      setDone(true);
      setPassword("");
      onDone();
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  }

  if (!open) {
    return (
      <Button type="button" className="min-h-11 px-3 text-[13px]" onClick={() => setOpen(true)}>
        Reset password
      </Button>
    );
  }

  return (
    <div className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 p-0 sm:items-center sm:p-4" role="dialog" aria-label="Reset password">
      <form onSubmit={submit} className="max-h-[90vh] w-full max-w-md overflow-y-auto rounded-t-2xl border border-line bg-surface p-5 shadow-lg sm:rounded-xl">
        <h2 className="m-0 mb-2 text-lg font-semibold">Reset password</h2>
        <p className="m-0 mb-4 text-sm text-muted">
          Set a new password for <strong>{user.name}</strong> ({user.username}). Tell them the new password — they will
          be signed out everywhere.
        </p>
        {done ? (
          <Button type="button" variant="primary" className="min-h-11 w-full" onClick={() => { setOpen(false); setDone(false); }}>
            Close
          </Button>
        ) : (
          <>
            <Field
              label="New password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              minLength={4}
              autoComplete="new-password"
            />
            <ErrorNote message={error} />
            <div className="mt-4 flex flex-col gap-2 sm:flex-row">
              <Button type="button" className="min-h-11 flex-1" onClick={() => setOpen(false)} disabled={busy}>
                Cancel
              </Button>
              <Button type="submit" variant="primary" className="min-h-11 flex-1" disabled={busy}>
                {busy ? "Saving…" : "Reset password"}
              </Button>
            </div>
          </>
        )}
      </form>
    </div>
  );
}

function AddUser({ onCreated }: { onCreated: () => void }) {
  const empty = { name: "", username: "", password: "", role: "CASHIER" as Role, webAccess: true };
  const [f, setF] = useState(empty);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function submit(e: FormEvent) {
    e.preventDefault();
    setBusy(true);
    setError(null);
    try {
      await adminApi.createUser(f);
      setF(empty);
      onCreated();
    } catch (err) {
      setError(errorText(err));
    } finally {
      setBusy(false);
    }
  }

  return (
    <form onSubmit={submit} className="flex min-w-0 flex-col gap-4">
      <h2 className="m-0 text-[17px] font-semibold xl:hidden">Add user</h2>
      <Field label="Full name" value={f.name} onChange={(e) => setF({ ...f, name: e.target.value })} required />
      <Field label="Username" value={f.username} onChange={(e) => setF({ ...f, username: e.target.value })} required autoComplete="off" />
      <Field label="Password or PIN (min 4)" type="password" value={f.password} onChange={(e) => setF({ ...f, password: e.target.value })} required minLength={4} autoComplete="new-password" />
      <fieldset className="m-0 flex flex-col gap-2 border-0 p-0">
        <legend className="mb-2 text-[13px] font-medium">Role</legend>
        <div className="flex flex-wrap gap-2">
          {(["CASHIER", "MANAGER", "ADMIN"] as Role[]).map((r) => (
            <button
              key={r}
              type="button"
              aria-pressed={f.role === r}
              onClick={() => setF({ ...f, role: r })}
              className={`min-h-11 rounded-full px-4 text-[13px] font-semibold ${
                f.role === r ? "border border-brand bg-brand text-white" : "border border-[#b9c2bd] bg-surface"
              }`}
            >
              {r.charAt(0) + r.slice(1).toLowerCase()}
            </button>
          ))}
        </div>
        <span className="text-xs leading-relaxed text-muted">{roleHints[f.role]}</span>
      </fieldset>
      <label className="flex min-h-11 cursor-pointer items-center justify-between gap-3">
        <span className="flex flex-col">
          <span className="text-sm font-medium">Allow web sign-in</span>
          <span className="text-xs text-muted">From Wi-Fi or the public link</span>
        </span>
        <input type="checkbox" checked={f.webAccess} onChange={(e) => setF({ ...f, webAccess: e.target.checked })} className="size-5 accent-brand" />
      </label>
      <ErrorNote message={error} />
      <Button type="submit" variant="primary" className="min-h-11" disabled={busy}>
        Create user
      </Button>
    </form>
  );
}
