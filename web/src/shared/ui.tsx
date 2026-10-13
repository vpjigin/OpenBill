import type { ButtonHTMLAttributes, InputHTMLAttributes, ReactNode } from "react";
import { useId } from "react";

type Variant = "primary" | "secondary" | "danger" | "ghost";

const variants: Record<Variant, string> = {
  primary: "bg-brand text-white border border-brand hover:bg-brand-strong",
  secondary: "bg-surface text-ink border border-[#b9c2bd] hover:bg-ground",
  danger: "bg-surface text-danger border border-danger-line hover:bg-[#fbf1ef]",
  ghost: "bg-transparent text-ink border border-transparent hover:bg-ground",
};

export function Button({
  variant = "secondary",
  className = "",
  ...props
}: ButtonHTMLAttributes<HTMLButtonElement> & { variant?: Variant }) {
  return (
    <button
      type="button"
      {...props}
      className={`inline-flex min-h-11 items-center justify-center gap-2 rounded-lg px-4 text-sm font-semibold transition-colors disabled:cursor-not-allowed disabled:opacity-50 ${variants[variant]} ${className}`}
    />
  );
}

export function Field({
  label,
  hint,
  className = "",
  ...props
}: InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: string }) {
  const id = useId();
  return (
    <div className={`flex flex-col gap-1.5 ${className}`}>
      <label htmlFor={id} className="text-[13px] font-medium">
        {label}
      </label>
      <input
        id={id}
        {...props}
        className="min-h-11 rounded-lg border border-[#b9c2bd] bg-surface px-3 text-base outline-none focus:border-brand focus:ring-2 focus:ring-brand/20 sm:text-sm"
      />
      {hint && <span className="text-xs text-muted">{hint}</span>}
    </div>
  );
}

export function Card({ title, actions, children, className = "" }: {
  title?: ReactNode;
  actions?: ReactNode;
  children: ReactNode;
  className?: string;
}) {
  return (
    <section className={`rounded-xl border border-line bg-surface ${className}`}>
      {(title || actions) && (
        <div className="flex flex-col gap-3 border-b border-line-soft px-4 py-4 sm:flex-row sm:items-center sm:justify-between sm:px-5">
          {title && <h2 className="m-0 text-[17px] font-semibold">{title}</h2>}
          {actions && <div className="flex min-w-0 flex-wrap items-center gap-2">{actions}</div>}
        </div>
      )}
      {children}
    </section>
  );
}

export function Badge({ tone = "neutral", children }: { tone?: "ok" | "neutral" | "warn"; children: ReactNode }) {
  const tones = {
    ok: "bg-brand-soft text-brand",
    neutral: "bg-[#eef0ee] text-muted",
    warn: "bg-warn-bg text-warn-ink",
  };
  return <span className={`shrink-0 rounded-full px-2.5 py-1 text-xs font-semibold ${tones[tone]}`}>{children}</span>;
}

export function ErrorNote({ message }: { message: string | null }) {
  if (!message) return null;
  return (
    <p role="alert" className="m-0 rounded-lg bg-[#fbf1ef] px-3 py-2.5 text-[13px] text-danger">
      {message}
    </p>
  );
}

export function ListPager({
  page,
  size,
  total,
  onPage,
}: {
  page: number;
  size: number;
  total: number;
  onPage: (page: number) => void;
}) {
  const pageCount = Math.max(1, Math.ceil(total / size));
  if (total <= size) return null;
  return (
    <div className="flex flex-wrap items-center justify-end gap-2 border-t border-line-soft px-5 py-3 text-sm">
      <Button type="button" className="min-h-9 px-3 text-[13px]" disabled={page <= 0} onClick={() => onPage(page - 1)}>
        Previous
      </Button>
      <span className="text-muted">
        Page {page + 1} of {pageCount}
      </span>
      <Button
        type="button"
        className="min-h-9 px-3 text-[13px]"
        disabled={page + 1 >= pageCount}
        onClick={() => onPage(page + 1)}
      >
        Next
      </Button>
    </div>
  );
}

export { Page as PageHeader, FormPageWrap, StatGrid, StatCard, Toolbar, ToolbarField, TabPills } from "./layout";

export function errorText(e: unknown): string {
  return e instanceof Error ? e.message : "Something went wrong";
}
