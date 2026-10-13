import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import { FormPageWrap } from "../../shared/layout";
import { Button, Card, ErrorNote, Field, PageHeader, errorText } from "../../shared/ui";
import { GST_STATES } from "../../shared/gstStates";
import { adminApi } from "../adminApi";

export function SettingsPage() {
  const [f, setF] = useState({
    shopName: "",
    gstin: "",
    stateCode: "",
    address: "",
    phone: "",
    pincode: "",
    invoicePrefix: "INV",
    publicUrl: "",
    tunnelToken: "",
  });
  const [tokenSet, setTokenSet] = useState(false);
  const [hasLogo, setHasLogo] = useState(false);
  const [receiptShowLogo, setReceiptShowLogo] = useState(true);
  const [logoPreviewUrl, setLogoPreviewUrl] = useState<string | null>(null);
  const [logoTick, setLogoTick] = useState(0);
  const [msg, setMsg] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    adminApi.settings().then((s) => {
      setF({
        shopName: s.shopName,
        gstin: s.gstin ?? "",
        stateCode: s.stateCode ?? "",
        address: s.address ?? "",
        phone: s.phone ?? "",
        pincode: s.pincode ?? "",
        invoicePrefix: s.invoicePrefix,
        publicUrl: s.publicUrl ?? "",
        tunnelToken: "",
      });
      setTokenSet(s.tunnelTokenSet);
      setHasLogo(s.hasLogo);
      setReceiptShowLogo(s.receiptShowLogo);
    });
  }, []);

  useEffect(() => {
    if (!hasLogo) {
      setLogoPreviewUrl((prev) => {
        if (prev) URL.revokeObjectURL(prev);
        return null;
      });
      return;
    }
    const token = localStorage.getItem("billing.token");
    let objectUrl: string | null = null;
    let cancelled = false;
    fetch(adminApi.logoPreviewUrl(), {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    })
      .then((r) => (r.ok ? r.blob() : null))
      .then((blob) => {
        if (cancelled || !blob) return;
        objectUrl = URL.createObjectURL(blob);
        setLogoPreviewUrl((prev) => {
          if (prev) URL.revokeObjectURL(prev);
          return objectUrl;
        });
      })
      .catch(() => {
        if (!cancelled) setLogoPreviewUrl(null);
      });
    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [hasLogo, logoTick]);

  const set = (k: keyof typeof f) => (e: { target: { value: string } }) => setF({ ...f, [k]: e.target.value });

  async function submit(e: FormEvent) {
    e.preventDefault();
    setMsg(null);
    setError(null);
    try {
      // Empty token field = keep the saved token.
      const s = await adminApi.saveSettings({
        ...f,
        tunnelToken: f.tunnelToken ? f.tunnelToken : null,
        receiptShowLogo,
      });
      setTokenSet(s.tunnelTokenSet);
      setHasLogo(s.hasLogo);
      setReceiptShowLogo(s.receiptShowLogo);
      setF({ ...f, tunnelToken: "" });
      setMsg("Saved");
    } catch (err) {
      setError(errorText(err));
    }
  }

  return (
    <FormPageWrap>
    <form onSubmit={submit} className="flex w-full min-w-0 flex-col gap-5">
      <PageHeader title="Shop & GST settings" actions={<Button type="submit" variant="primary">Save</Button>} />
      {msg && <p className="m-0 text-sm text-brand">{msg}</p>}
      <ErrorNote message={error} />
      <Card title="Shop">
        <div className="grid grid-cols-1 gap-4 p-5 md:grid-cols-2">
          <Field label="Shop name" value={f.shopName} onChange={set("shopName")} required />
          <Field label="Phone" value={f.phone} onChange={set("phone")} />
          <Field label="GSTIN" value={f.gstin} onChange={set("gstin")} maxLength={15} />
          <div className="flex flex-col gap-1.5">
            <label htmlFor="shop-state" className="text-[13px] font-medium">State</label>
            <select
              id="shop-state"
              value={f.stateCode}
              onChange={set("stateCode")}
              className="min-h-11 rounded-lg border border-[#b9c2bd] bg-surface px-3 text-sm"
            >
              <option value="">—</option>
              {GST_STATES.map((s) => (
                <option key={s.code} value={s.code}>
                  {s.code} — {s.name}
                </option>
              ))}
            </select>
            <span className="text-xs text-muted">Decides CGST+SGST vs IGST on bills when customer state is known.</span>
          </div>
          <Field label="Address" className="md:col-span-2" value={f.address} onChange={set("address")} />
          <Field
            label="Pincode"
            hint="Required for e-invoice JSON export (6 digits)."
            value={f.pincode}
            onChange={set("pincode")}
            inputMode="numeric"
            pattern="[0-9]{6}"
            maxLength={6}
          />
          <Field
            label="Invoice prefix"
            hint="Numbers look like INV/26-27/000001 and restart every April."
            value={f.invoicePrefix}
            onChange={(e) => setF({ ...f, invoicePrefix: e.target.value.toUpperCase() })}
            pattern="[A-Z0-9-]{1,8}"
            required
          />
        </div>
      </Card>
      <Card title="Logo on invoices">
        <div className="flex flex-col gap-4 p-5">
          <p className="m-0 text-sm text-muted">
            PNG or JPEG, max 200 KB. Shown on A4 prints; 80 mm receipts when enabled below.
          </p>
          {logoPreviewUrl && (
            <img
              src={logoPreviewUrl}
              alt="Shop logo preview"
              className="max-h-24 max-w-[200px] rounded border border-line object-contain"
            />
          )}
          <div className="flex flex-wrap gap-2">
            <label className="inline-flex cursor-pointer items-center rounded-lg bg-brand px-4 py-2.5 text-sm font-semibold text-white">
              <input
                type="file"
                accept="image/png,image/jpeg"
                className="sr-only"
                onChange={(e) => {
                  const file = e.target.files?.[0];
                  if (!file) return;
                  setError(null);
                  adminApi
                    .uploadLogo(file)
                    .then((s) => {
                      setHasLogo(s.hasLogo);
                      setLogoTick((t) => t + 1);
                      setMsg("Logo uploaded");
                    })
                    .catch((err) => setError(errorText(err)));
                  e.target.value = "";
                }}
              />
              Upload logo
            </label>
            {hasLogo && (
              <Button
                type="button"
                onClick={() => {
                  setError(null);
                  adminApi
                    .removeLogo()
                    .then((s) => {
                      setHasLogo(s.hasLogo);
                      setLogoTick((t) => t + 1);
                      setMsg("Logo removed");
                    })
                    .catch((err) => setError(errorText(err)));
                }}
              >
                Remove logo
              </Button>
            )}
          </div>
          <label className="flex items-center gap-2 text-sm">
            <input
              type="checkbox"
              checked={receiptShowLogo}
              onChange={(e) => setReceiptShowLogo(e.target.checked)}
            />
            Show logo on 80 mm receipt print
          </label>
        </div>
      </Card>
      <Card title="Public access (Cloudflare Tunnel)">
        <div className="grid grid-cols-1 gap-4 p-5 md:grid-cols-2">
          <div className="md:col-span-2 rounded-lg border border-line-soft bg-ground px-4 py-3 text-[13px] leading-relaxed text-muted">
            <p className="mt-0 font-medium text-ink">Fixed link (recommended for a real shop)</p>
            <ol className="mb-0 mt-2 list-decimal space-y-1.5 pl-5">
              <li>
                In the{" "}
                <a href="https://one.dash.cloudflare.com/" className="text-brand" target="_blank" rel="noreferrer">
                  Cloudflare Zero Trust dashboard
                </a>
                , go to <strong>Networks → Tunnels</strong> and create a tunnel.
              </li>
              <li>
                Add a <strong>Public Hostname</strong> that points to{" "}
                <span className="font-mono">http://127.0.0.1:8081</span> (this app&apos;s public port on this PC).
              </li>
              <li>Copy the tunnel <strong>token</strong> and your public hostname URL into the fields below, then Save.</li>
              <li>On <strong>Server &amp; access</strong>, start <strong>Public (internet)</strong>.</li>
            </ol>
            <p className="mb-0 mt-3">
              Leave the token empty to use a free temporary <span className="font-mono">trycloudflare.com</span> link (fine for
              testing; the URL changes every time you start Public).
            </p>
          </div>
          <Field
            label={tokenSet ? "Tunnel token (saved — type to replace)" : "Tunnel token"}
            type="password"
            value={f.tunnelToken}
            onChange={set("tunnelToken")}
            autoComplete="off"
          />
          <Field label="Public URL" placeholder="https://shop.example.in" value={f.publicUrl} onChange={set("publicUrl")} />
        </div>
      </Card>
    </form>
    </FormPageWrap>
  );
}
