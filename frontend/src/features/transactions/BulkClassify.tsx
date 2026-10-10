import { useState } from "react";
import type { CategoryNode } from "../../api/categories";
import { classifyTransactions, type Allocation, type AllocationKind, type Transaction } from "../../api/transactions";
import { formatMoney, sumMoney } from "../../lib/format";
import { CategorySelect, HAS_CATEGORIES, KIND_HINTS, KINDS } from "./ClassifyTransaction";
import { KIND_LABELS } from "./labels";

type SubmitState = { status: "idle" } | { status: "saving" } | { status: "error"; message: string };

interface BulkClassifyProps {
  transactions: Transaction[];
  categories: CategoryNode[];
  onSaved: (saved: Record<string, Allocation[]>) => void;
  onCancel: () => void;
}

/** One kind and category for every selected transaction, each as a single piece for its whole amount. */
export function BulkClassify({ transactions, categories, onSaved, onCancel }: BulkClassifyProps) {
  const [kind, setKind] = useState<AllocationKind>(() =>
    transactions.every((transaction) => transaction.amount > 0) ? "INCOME" : "EXPENSE",
  );
  const [categoryId, setCategoryId] = useState<string | null>(null);
  const [note, setNote] = useState("");
  const [submit, setSubmit] = useState<SubmitState>({ status: "idle" });

  const out = sumMoney(transactions.filter((t) => t.amount < 0).map((t) => -t.amount));
  const moneyIn = sumMoney(transactions.filter((t) => t.amount > 0).map((t) => t.amount));
  const classified = transactions.filter((t) => t.allocations.length > 0).length;
  const splits = transactions.filter((t) => t.allocations.length > 1).length;
  const refundLinks = transactions.filter((t) => t.refundedPurchases.length > 0).length;

  async function handleSubmit(event: { preventDefault(): void }) {
    event.preventDefault();
    setSubmit({ status: "saving" });
    try {
      onSaved(
        await classifyTransactions({
          transactionIds: transactions.map((transaction) => transaction.id),
          kind,
          categoryId: HAS_CATEGORIES[kind] ? categoryId : null,
          note: note.trim() === "" ? null : note.trim(),
        }),
      );
    } catch (error: unknown) {
      setSubmit({ status: "error", message: error instanceof Error ? error.message : "Unknown error" });
    }
  }

  return (
    <form className="form" onSubmit={handleSubmit}>
      <div className="classify__summary">
        <p className="classify__narration">
          {transactions.length} transaction{transactions.length === 1 ? "" : "s"}
        </p>
        <p className="classify__amount num">
          {out > 0 && <span className="amount amount--out">{formatMoney(out)} out</span>}
          {out > 0 && moneyIn > 0 && " · "}
          {moneyIn > 0 && <span className="amount amount--in">{formatMoney(moneyIn)} in</span>}
        </p>
      </div>

      <div className="bulk__fields">
        <label className="field">
          <span className="field__label">Kind</span>
          <select
            className="input"
            value={kind}
            onChange={(event) => {
              setKind(event.target.value as AllocationKind);
              setCategoryId(null);
            }}
          >
            {KINDS.map((option) => (
              <option key={option} value={option}>
                {KIND_LABELS[option]}
              </option>
            ))}
          </select>
        </label>
        {HAS_CATEGORIES[kind] ? (
          <label className="field">
            <span className="field__label">Category</span>
            <CategorySelect categories={categories} kind={kind} allowed={null} value={categoryId} onChange={setCategoryId} />
          </label>
        ) : (
          <div className="field">
            <span className="field__label">Category</span>
            <p className="piece__hint">{KIND_HINTS[kind]}</p>
          </div>
        )}
        <label className="field bulk__note">
          <span className="field__label">
            Note<span className="field__optional">optional</span>
          </span>
          <input className="input" value={note} onChange={(event) => setNote(event.target.value)} maxLength={200} />
        </label>
      </div>

      <p className="form__hint">
        Each transaction becomes one piece for its full amount. Split or link a refund one transaction at a time.
      </p>
      {classified > 0 && (
        <p className="form__hint bulk__warning">
          {classified} of these {classified === 1 ? "is" : "are"} already classified
          {splits > 0 && ` (${splits} as a split)`}
          {refundLinks > 0 && `, ${refundLinks} as a linked refund`}. Saving replaces that.
        </p>
      )}

      {submit.status === "error" && (
        <p className="form__error" role="alert">
          {submit.message}
        </p>
      )}

      <div className="dialog__actions">
        <button type="button" className="button button--secondary" onClick={onCancel}>
          Cancel
        </button>
        <button type="submit" className="button" disabled={submit.status === "saving"}>
          {submit.status === "saving" ? "Saving…" : `Classify ${transactions.length}`}
        </button>
      </div>
    </form>
  );
}
