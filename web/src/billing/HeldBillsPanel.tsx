import { formatPaise } from "../shared/money";
import { Button } from "../shared/ui";
import type { HeldBillSummary } from "./billingApi";

export function HeldBillsPanel({
  bills,
  onResume,
  onDiscard,
  onClose,
}: {
  bills: HeldBillSummary[];
  onResume: (b: HeldBillSummary) => void;
  onDiscard: (b: HeldBillSummary) => void;
  onClose: () => void;
}) {
  return (
    <div
      className="fixed inset-0 z-50 flex items-end justify-center bg-black/40 p-0 sm:items-start sm:p-4 sm:pt-16"
      role="dialog"
      aria-label="Held bills"
    >
      <div className="flex max-h-[92vh] w-full min-w-0 flex-col overflow-hidden rounded-t-2xl border border-line bg-surface shadow-lg sm:max-h-[80vh] sm:max-w-lg sm:rounded-xl">
        <div className="flex items-center justify-between border-b border-line-soft px-4 py-3">
          <h2 className="m-0 text-lg font-semibold">Held bills</h2>
          <Button type="button" className="min-h-11 min-w-11 px-3 text-[13px]" onClick={onClose}>
            Close
          </Button>
        </div>
        <ul className="m-0 min-w-0 flex-1 list-none overflow-y-auto p-0">
          {bills.length === 0 ? (
            <li className="px-4 py-8 text-center text-sm text-muted">No held bills</li>
          ) : (
            bills.map((b) => (
              <li key={b.id} className="border-t border-line-soft px-4 py-3">
                <div className="flex min-w-0 flex-col gap-3 sm:flex-row sm:items-start sm:justify-between">
                  <div className="min-w-0">
                    <div className="font-medium">{b.label ?? "Held bill"}</div>
                    <div className="text-[13px] text-muted">
                      {b.itemCount} item{b.itemCount === 1 ? "" : "s"}
                      {b.previewTotalPaise != null && <> · {formatPaise(b.previewTotalPaise)}</>}
                    </div>
                    <div className="text-[12px] text-muted">
                      {b.createdByName} · {new Date(b.createdAt).toLocaleString("en-IN")}
                    </div>
                  </div>
                  <div className="flex w-full shrink-0 flex-col gap-2 sm:w-auto sm:flex-row">
                    <Button
                      type="button"
                      variant="primary"
                      className="min-h-11 w-full px-3 text-[13px] sm:w-auto"
                      onClick={() => onResume(b)}
                    >
                      Resume
                    </Button>
                    <Button type="button" className="min-h-11 w-full px-3 text-[13px] sm:w-auto" onClick={() => onDiscard(b)}>
                      Discard
                    </Button>
                  </div>
                </div>
              </li>
            ))
          )}
        </ul>
      </div>
    </div>
  );
}
