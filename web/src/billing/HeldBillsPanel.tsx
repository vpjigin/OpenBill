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
    <div className="fixed inset-0 z-50 flex items-start justify-center bg-black/40 p-4 pt-16" role="dialog" aria-label="Held bills">
      <div className="max-h-[80vh] w-full max-w-lg overflow-hidden rounded-xl border border-line bg-surface shadow-lg">
        <div className="flex items-center justify-between border-b border-line-soft px-4 py-3">
          <h2 className="m-0 text-lg font-semibold">Held bills</h2>
          <Button type="button" className="min-h-9 px-3 text-[13px]" onClick={onClose}>
            Close
          </Button>
        </div>
        <ul className="m-0 max-h-[60vh] list-none overflow-y-auto p-0">
          {bills.length === 0 ? (
            <li className="px-4 py-8 text-center text-sm text-muted">No held bills</li>
          ) : (
            bills.map((b) => (
              <li key={b.id} className="border-t border-line-soft px-4 py-3">
                <div className="flex flex-wrap items-start justify-between gap-2">
                  <div>
                    <div className="font-medium">{b.label ?? "Held bill"}</div>
                    <div className="text-[13px] text-muted">
                      {b.itemCount} item{b.itemCount === 1 ? "" : "s"}
                      {b.previewTotalPaise != null && (
                        <> · {formatPaise(b.previewTotalPaise)}</>
                      )}
                    </div>
                    <div className="text-[12px] text-muted">
                      {b.createdByName} · {new Date(b.createdAt).toLocaleString("en-IN")}
                    </div>
                  </div>
                  <div className="flex shrink-0 gap-2">
                    <Button type="button" variant="primary" className="min-h-9 px-3 text-[13px]" onClick={() => onResume(b)}>
                      Resume
                    </Button>
                    <Button type="button" className="min-h-9 px-3 text-[13px]" onClick={() => onDiscard(b)}>
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
