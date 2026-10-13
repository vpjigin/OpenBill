import type { ReactNode } from "react";
import { useEffect, useId, useState } from "react";
import { Button } from "./ui";

/** Page title block; actions wrap under the title on small screens. */
export function Page({
  title,
  subtitle,
  actions,
  children,
}: {
  title: string;
  subtitle?: string;
  actions?: ReactNode;
  children?: ReactNode;
}) {
  return (
    <div className="flex w-full min-w-0 flex-col gap-5">
      <header className="flex flex-col gap-3 sm:flex-row sm:flex-wrap sm:items-end sm:justify-between">
        <div className="min-w-0 flex flex-col gap-1">
          <h1 className="m-0 text-2xl font-semibold sm:text-[26px]">{title}</h1>
          {subtitle && <p className="m-0 text-base text-muted sm:text-sm">{subtitle}</p>}
        </div>
        {actions && <div className="flex w-full min-w-0 flex-wrap items-center gap-2 sm:w-auto">{actions}</div>}
      </header>
      {children}
    </div>
  );
}

/** Filters and tool rows; controls go full width on mobile. */
export function Toolbar({ children, className = "" }: { children: ReactNode; className?: string }) {
  return (
    <div className={`flex w-full min-w-0 flex-wrap items-end gap-3 [&_input]:min-h-11 [&_input]:w-full [&_input]:text-base [&_input]:sm:w-auto [&_input]:sm:text-sm [&_select]:min-h-11 [&_select]:w-full [&_select]:text-base [&_select]:sm:w-auto [&_select]:sm:text-sm ${className}`}>
      {children}
    </div>
  );
}

export function ToolbarField({ label, children }: { label: string; children: ReactNode }) {
  return (
    <label className="flex min-w-0 flex-1 flex-col gap-1 text-sm sm:flex-none sm:min-w-[10rem]">
      <span className="text-muted">{label}</span>
      {children}
    </label>
  );
}

export function StatGrid({ children, className = "" }: { children: ReactNode; className?: string }) {
  return (
    <div
      data-testid="stat-grid"
      className={`grid w-full min-w-0 grid-cols-2 gap-3 lg:grid-cols-4 ${className}`}
    >
      {children}
    </div>
  );
}

export function StatCard({ label, value }: { label: string; value: ReactNode }) {
  return (
    <div className="rounded-xl border border-line bg-surface px-4 py-3">
      <div className="text-xs text-muted">{label}</div>
      <div className="font-mono text-lg font-medium text-ink">{value}</div>
    </div>
  );
}

/** Settings / narrow forms — full width up to max-w-3xl. */
export function FormPageWrap({ children }: { children: ReactNode }) {
  return <div className="mx-auto w-full min-w-0 max-w-3xl">{children}</div>;
}

/** List + side form: side-by-side ≥1280px; stacked on smaller; mobile form in sheet. */
export function SplitLayout({
  list,
  form,
  formTitle,
  addLabel = "Add",
}: {
  list: ReactNode;
  form: ReactNode;
  formTitle: string;
  addLabel?: string;
}) {
  const [sheetOpen, setSheetOpen] = useState(false);

  useEffect(() => {
    const mq = window.matchMedia("(min-width: 1280px)");
    const onChange = () => {
      if (mq.matches) setSheetOpen(false);
    };
    mq.addEventListener("change", onChange);
    return () => mq.removeEventListener("change", onChange);
  }, []);

  return (
    <div className="flex w-full min-w-0 flex-col gap-5 xl:flex-row xl:items-stretch xl:gap-6">
      <div className="order-2 min-w-0 flex-1 xl:order-1">{list}</div>
      <div className="order-1 xl:order-2 xl:w-[380px] xl:shrink-0">
        <div className="xl:sticky xl:top-6">
          <div className="mb-3 xl:hidden">
            <Button type="button" variant="primary" className="min-h-11 w-full" onClick={() => setSheetOpen(true)}>
              {addLabel}
            </Button>
          </div>
          <div className="hidden xl:block">{form}</div>
        </div>
      </div>
      {sheetOpen && (
        <div className="fixed inset-0 z-50 flex flex-col bg-black/40 xl:hidden" role="presentation">
          <button type="button" className="min-h-11 flex-1" aria-label="Close" onClick={() => setSheetOpen(false)} />
          <div className="max-h-[90vh] overflow-y-auto rounded-t-2xl border border-line bg-surface p-5 shadow-xl">
            <div className="mb-4 flex items-center justify-between gap-2">
              <h2 className="m-0 text-lg font-semibold">{formTitle}</h2>
              <Button type="button" className="min-h-11 min-w-11 px-3" onClick={() => setSheetOpen(false)}>
                Done
              </Button>
            </div>
            {form}
          </div>
        </div>
      )}
    </div>
  );
}

export type DataColumn<T> = {
  key: string;
  header: string;
  cell: (row: T) => ReactNode;
  /** Shown on mobile card; defaults to header. */
  mobileLabel?: string;
  mobilePrimary?: boolean;
  /** Right-aligned mono amount on mobile card. */
  mobileAmount?: boolean;
  className?: string;
  hideOnMobile?: boolean;
};

export function DataTable<T>({
  columns,
  rows,
  rowKey,
  onRowClick,
  rowActions,
  emptyMessage = "No rows.",
}: {
  columns: DataColumn<T>[];
  rows: T[];
  rowKey: (row: T) => string;
  onRowClick?: (row: T) => void;
  rowActions?: (row: T) => ReactNode;
  emptyMessage?: string;
}) {
  const tableId = useId();
  const mobileCols = columns.filter((c) => !c.hideOnMobile);

  return (
    <div className="w-full min-w-0" data-testid="data-table">
      <div className="hidden w-full min-w-0 overflow-x-auto md:block">
        <table className="w-full border-collapse text-sm" aria-labelledby={tableId}>
          <thead>
            <tr className="text-left text-xs uppercase tracking-wider text-muted">
              {columns.map((c) => (
                <th key={c.key} className={`px-4 py-2.5 font-medium ${c.className ?? ""}`}>
                  {c.header}
                </th>
              ))}
              {rowActions && <th className="px-4 py-2.5 text-right font-medium">Action</th>}
            </tr>
          </thead>
          <tbody>
            {rows.length === 0 && (
              <tr className="border-t border-line-soft">
                <td colSpan={columns.length + (rowActions ? 1 : 0)} className="px-4 py-6 text-muted">
                  {emptyMessage}
                </td>
              </tr>
            )}
            {rows.map((row) => (
              <tr
                key={rowKey(row)}
                className={`border-t border-line-soft ${onRowClick ? "cursor-pointer hover:bg-brand-soft/40" : ""}`}
                onClick={onRowClick ? () => onRowClick(row) : undefined}
              >
                {columns.map((c) => (
                  <td key={c.key} className={`px-4 py-3 ${c.className ?? ""}`}>
                    {c.cell(row)}
                  </td>
                ))}
                {rowActions && (
                  <td className="px-4 py-3 text-right" onClick={(e) => e.stopPropagation()}>
                    {rowActions(row)}
                  </td>
                )}
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      <div className="flex flex-col gap-2 md:hidden">
        {rows.length === 0 && <p className="m-0 px-1 py-4 text-sm text-muted">{emptyMessage}</p>}
        {rows.map((row) => {
          const primary = mobileCols.find((c) => c.mobilePrimary) ?? mobileCols[0];
          const amount = mobileCols.find((c) => c.mobileAmount);
          const rest = mobileCols.filter((c) => c !== primary && c !== amount);
          return (
            <div
              key={rowKey(row)}
              className={`rounded-xl border border-line bg-surface p-4 ${onRowClick ? "active:bg-brand-soft/30" : ""}`}
              onClick={onRowClick ? () => onRowClick(row) : undefined}
              onKeyDown={
                onRowClick
                  ? (e) => {
                      if (e.key === "Enter" || e.key === " ") {
                        e.preventDefault();
                        onRowClick(row);
                      }
                    }
                  : undefined
              }
              role={onRowClick ? "button" : undefined}
              tabIndex={onRowClick ? 0 : undefined}
            >
              <div className="flex items-start justify-between gap-2">
                <div className="min-w-0 flex-1 font-semibold text-ink">{primary ? primary.cell(row) : null}</div>
                {amount && <div className="shrink-0 font-mono text-sm font-medium">{amount.cell(row)}</div>}
                {rowActions && (
                  <div onClick={(e) => e.stopPropagation()} onKeyDown={(e) => e.stopPropagation()}>
                    <RowActionsMenu>{rowActions(row)}</RowActionsMenu>
                  </div>
                )}
              </div>
              <dl className="mt-2 grid grid-cols-[auto_1fr] gap-x-3 gap-y-1 text-sm">
                {rest.slice(0, 3).map((c) => (
                  <div key={c.key} className="contents">
                    <dt className="text-muted">{c.mobileLabel ?? c.header}</dt>
                    <dd className="m-0 min-w-0">{c.cell(row)}</dd>
                  </div>
                ))}
              </dl>
            </div>
          );
        })}
      </div>
    </div>
  );
}

function RowActionsMenu({ children }: { children: ReactNode }) {
  const [open, setOpen] = useState(false);
  return (
    <div className="relative">
      <button
        type="button"
        aria-label="Actions"
        className="flex min-h-11 min-w-11 items-center justify-center rounded-lg border border-line text-lg leading-none"
        onClick={() => setOpen((v) => !v)}
      >
        ⋯
      </button>
      {open && (
        <>
          <button type="button" className="fixed inset-0 z-10" aria-label="Close menu" onClick={() => setOpen(false)} />
          <div className="absolute right-0 z-20 flex min-w-[10rem] flex-col gap-1 rounded-lg border border-line bg-surface p-2 shadow-lg">
            {children}
          </div>
        </>
      )}
    </div>
  );
}

/** Horizontally scrollable tab pills (Reports, etc.). */
export function TabPills({
  tabs,
  active,
  onChange,
}: {
  tabs: { id: string; label: string }[];
  active: string;
  onChange: (id: string) => void;
}) {
  return (
    <div className="-mx-1 mb-4 overflow-x-auto px-1 pb-1">
      <div className="flex w-max min-w-full gap-2 sm:flex-wrap sm:w-auto">
        {tabs.map((t) => (
          <button
            key={t.id}
            type="button"
            onClick={() => onChange(t.id)}
            className={`min-h-11 shrink-0 rounded-full px-4 text-sm font-semibold ${
              active === t.id ? "bg-brand text-white" : "border border-line bg-surface text-ink"
            }`}
          >
            {t.label}
          </button>
        ))}
      </div>
    </div>
  );
}

/** Sticky bar for mobile detail pages (print controls). */
export function StickyActionBar({ children }: { children: ReactNode }) {
  return (
    <div className="sticky top-0 z-20 -mx-5 border-b border-line bg-ground/95 px-5 py-3 backdrop-blur-sm sm:-mx-8 sm:px-8 lg:static lg:mx-0 lg:border-0 lg:bg-transparent lg:p-0 lg:backdrop-blur-none">
      {children}
    </div>
  );
}

/** Bottom safe area padding when shell shows mobile tab bar. */
export function MobileTabSpacer() {
  return <div className="h-16 sm:hidden" aria-hidden />;
}
