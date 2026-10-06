import { useRef, useState } from "react";
import type { CategoryNode } from "../../api/categories";
import {
  classifyTransaction,
  type Allocation,
  type AllocationInput,
  type AllocationKind,
  type Transaction,
} from "../../api/transactions";
import { Icon } from "../../components/Icon";
import { formatLocalDate, formatMoney, formatSignedMoney } from "../../lib/format";
import { KIND_LABELS } from "./labels";

const KINDS: AllocationKind[] = ["EXPENSE", "INCOME", "TRANSFER", "INVESTMENT", "LENT"];

const HAS_CATEGORIES: Record<AllocationKind, boolean> = {
  EXPENSE: true,
  INCOME: true,
  TRANSFER: true,
  INVESTMENT: true,
  LENT: false,
};

const KIND_HINTS: Record<AllocationKind, string> = {
  EXPENSE: "Money you actually spent. Refunds go here too, in the original category.",
  INCOME: "Salary, interest, dividends and other earnings.",
  TRANSFER: "Moving money between your own accounts. Counts nowhere.",
  INVESTMENT: "SIPs, stocks, FDs, PPF and similar.",
  LENT: "Paid on someone's behalf, or lent. Repayments come back as Lent too.",
};

/** One row of the editor. The amount is text in the transaction's direction: "500" on a debit means ₹500 out. */
interface Piece {
  key: number;
  kind: AllocationKind;
  categoryId: string | null;
  amount: string;
  note: string;
}

type SubmitState = { status: "idle" } | { status: "saving" } | { status: "error"; message: string };

interface ClassifyTransactionProps {
  transaction: Transaction;
  categories: CategoryNode[];
  onSaved: (allocations: Allocation[]) => void;
  onCancel: () => void;
}

export function ClassifyTransaction({ transaction, categories, onSaved, onCancel }: ClassifyTransactionProps) {
  const direction = transaction.amount < 0 ? -1 : 1;
  const totalPaise = Math.round(Math.abs(transaction.amount) * 100);
  const [pieces, setPieces] = useState<Piece[]>(() => initialPieces(transaction, direction));
  const nextKey = useRef(pieces.length);
  const [submit, setSubmit] = useState<SubmitState>({ status: "idle" });

  const parsed = pieces.map((piece) => parsePaise(piece.amount));
  const allocatedPaise = parsed.reduce<number>((total, paise) => total + (paise ?? 0), 0);
  const remainingPaise = totalPaise - allocatedPaise;
  const everyAmountValid = parsed.every((paise) => paise !== null && paise !== 0);
  const canSave = everyAmountValid && remainingPaise === 0 && submit.status !== "saving";

  function update(key: number, changes: Partial<Piece>) {
    setPieces((current) => current.map((piece) => (piece.key === key ? { ...piece, ...changes } : piece)));
  }

  function addPiece() {
    const key = nextKey.current++;
    setPieces((current) => [
      ...current,
      {
        key,
        kind: direction < 0 ? "LENT" : "INCOME",
        categoryId: null,
        amount: remainingPaise > 0 ? toRupees(remainingPaise) : "",
        note: "",
      },
    ]);
  }

  async function save(body: AllocationInput[]) {
    setSubmit({ status: "saving" });
    try {
      onSaved(await classifyTransaction(transaction.id, body));
    } catch (error: unknown) {
      setSubmit({ status: "error", message: error instanceof Error ? error.message : "Unknown error" });
    }
  }

  function handleSubmit(event: { preventDefault(): void }) {
    event.preventDefault();
    if (!canSave) return;
    void save(
      pieces.map((piece, index) => ({
        kind: piece.kind,
        categoryId: HAS_CATEGORIES[piece.kind] ? piece.categoryId : null,
        amount: ((parsed[index] ?? 0) * direction) / 100,
        note: piece.note.trim() === "" ? null : piece.note.trim(),
      })),
    );
  }

  return (
    <form className="form" onSubmit={handleSubmit}>
      <div className="classify__summary">
        <div>
          <p className="classify__narration" title={transaction.description}>
            {transaction.description}
          </p>
          <p className="classify__date">{formatLocalDate(transaction.transactionDate)}</p>
        </div>
        <p className={`classify__amount amount ${direction < 0 ? "amount--out" : "amount--in"}`}>
          {formatSignedMoney(transaction.amount)}
        </p>
      </div>

      <ol className="pieces">
        {pieces.map((piece, index) => (
          <li key={piece.key} className="piece">
            <div className="piece__fields">
              <label className="field">
                <span className="field__label">Kind</span>
                <select
                  className="input"
                  value={piece.kind}
                  onChange={(event) =>
                    update(piece.key, { kind: event.target.value as AllocationKind, categoryId: null })
                  }
                >
                  {KINDS.map((kind) => (
                    <option key={kind} value={kind}>
                      {KIND_LABELS[kind]}
                    </option>
                  ))}
                </select>
              </label>

              {HAS_CATEGORIES[piece.kind] ? (
                <label className="field">
                  <span className="field__label">Category</span>
                  <select
                    className="input"
                    value={piece.categoryId ?? ""}
                    onChange={(event) =>
                      update(piece.key, { categoryId: event.target.value === "" ? null : event.target.value })
                    }
                  >
                    <option value="">No category</option>
                    {categories
                      .filter((parent) => parent.kind === piece.kind)
                      .map((parent) =>
                        parent.children.length === 0 ? (
                          <option key={parent.id} value={parent.id}>
                            {parent.name}
                          </option>
                        ) : (
                          <optgroup key={parent.id} label={parent.name}>
                            <option value={parent.id}>{parent.name} (general)</option>
                            {parent.children.map((child) => (
                              <option key={child.id} value={child.id}>
                                {child.name}
                              </option>
                            ))}
                          </optgroup>
                        ),
                      )}
                  </select>
                </label>
              ) : (
                <div className="field">
                  <span className="field__label">Category</span>
                  <p className="piece__hint">{KIND_HINTS[piece.kind]}</p>
                </div>
              )}

              <label className="field">
                <span className="field__label">Amount (₹)</span>
                <input
                  className="input piece__amount"
                  inputMode="decimal"
                  value={piece.amount}
                  onChange={(event) => update(piece.key, { amount: event.target.value })}
                  aria-invalid={parsed[index] === null || parsed[index] === 0}
                  required
                />
              </label>

              <label className="field piece__note">
                <span className="field__label">
                  Note<span className="field__optional">optional</span>
                </span>
                <input
                  className="input"
                  value={piece.note}
                  onChange={(event) => update(piece.key, { note: event.target.value })}
                  maxLength={200}
                  placeholder={piece.kind === "LENT" ? "Who owes you" : ""}
                />
              </label>
            </div>
            {pieces.length > 1 && (
              <button
                type="button"
                className="icon-button piece__remove"
                aria-label="Remove this piece"
                onClick={() => setPieces((current) => current.filter((other) => other.key !== piece.key))}
              >
                <Icon name="close" />
              </button>
            )}
          </li>
        ))}
      </ol>

      <div className="classify__bar">
        <button type="button" className="button button--ghost button--sm" onClick={addPiece}>
          <Icon name="split" size={16} /> Split into another piece
        </button>
        <p className={`classify__remaining${remainingPaise === 0 ? " classify__remaining--done" : ""}`}>
          {remainingPaise === 0 ? (
            <>
              <Icon name="check" size={16} /> Fully allocated
            </>
          ) : remainingPaise > 0 ? (
            <>{formatMoney(remainingPaise / 100)} left to allocate</>
          ) : (
            <>{formatMoney(-remainingPaise / 100)} too much</>
          )}
        </p>
      </div>

      {submit.status === "error" && (
        <p className="form__error" role="alert">
          {submit.message}
        </p>
      )}

      <div className="dialog__actions">
        {transaction.allocations.length > 0 && (
          <button
            type="button"
            className="button button--ghost classify__clear"
            disabled={submit.status === "saving"}
            onClick={() => void save([])}
          >
            Mark unclassified
          </button>
        )}
        <button type="button" className="button button--secondary" onClick={onCancel}>
          Cancel
        </button>
        <button type="submit" className="button" disabled={!canSave}>
          {submit.status === "saving" ? "Saving…" : "Save"}
        </button>
      </div>
    </form>
  );
}

function initialPieces(transaction: Transaction, direction: number): Piece[] {
  if (transaction.allocations.length === 0) {
    return [
      {
        key: 0,
        kind: direction < 0 ? "EXPENSE" : "INCOME",
        categoryId: null,
        amount: toRupees(Math.round(Math.abs(transaction.amount) * 100)),
        note: "",
      },
    ];
  }
  return transaction.allocations.map((allocation, index) => ({
    key: index,
    kind: allocation.kind,
    categoryId: allocation.categoryId,
    amount: toRupees(Math.round(allocation.amount * direction * 100)),
    note: allocation.note ?? "",
  }));
}

/**
 * "1,500.5" → 150050 paise. A leading minus means a piece going against the transaction's direction, like a cashback
 * inside a payment. Returns null for anything that isn't an amount with at most 2 decimals.
 */
function parsePaise(text: string): number | null {
  const cleaned = text.replace(/[,\s₹]/g, "");
  if (!/^-?\d+(\.\d{1,2})?$/.test(cleaned)) return null;
  return Math.round(Number(cleaned) * 100);
}

function toRupees(paise: number): string {
  return (paise / 100).toFixed(2);
}
