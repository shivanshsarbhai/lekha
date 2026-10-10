import { useEffect, useRef, useState } from "react";
import type { CategoryNode } from "../../api/categories";
import {
  classifyTransaction,
  listRefundCandidates,
  type Allocation,
  type AllocationInput,
  type AllocationKind,
  type RefundCandidate,
  type Transaction,
} from "../../api/transactions";
import { Icon } from "../../components/Icon";
import { formatLocalDate, formatMoney, formatSignedMoney } from "../../lib/format";
import { merchantName } from "../../lib/merchant";
import { KIND_LABELS } from "./labels";

/** REFUND exists only in this editor: it is saved as an EXPENSE piece linked to the purchase it refunds. */
type PieceKind = AllocationKind | "REFUND";

export const KINDS: AllocationKind[] = ["EXPENSE", "INCOME", "TRANSFER", "INVESTMENT", "LENT"];

const PIECE_LABELS: Record<PieceKind, string> = { ...KIND_LABELS, REFUND: "Refund" };

export const HAS_CATEGORIES: Record<PieceKind, boolean> = {
  EXPENSE: true,
  INCOME: true,
  TRANSFER: true,
  INVESTMENT: true,
  LENT: false,
  REFUND: true,
};

export const KIND_HINTS: Record<PieceKind, string> = {
  EXPENSE: "Money you actually spent. A refund you can't match to a purchase goes here, in its category.",
  INCOME: "Salary, interest, dividends and other earnings.",
  TRANSFER: "Moving money between your own accounts. Counts nowhere.",
  INVESTMENT: "SIPs, stocks, FDs, PPF and similar.",
  LENT: "Paid on someone's behalf, or lent. Repayments come back as Lent too.",
  REFUND: "Money back for an earlier purchase. It lowers spending in the purchase's month.",
};

/**
 * The purchase a refund piece is linked to. `refundable` and `categoryIds` are null for a saved link until the
 * candidates load, or if the purchase is too old to be listed; the server still checks those links.
 */
interface LinkedPurchase {
  id: string;
  description: string;
  transactionDate: string;
  refundable: number | null;
  categoryIds: string[] | null;
}

/** One row of the editor. The amount is text in the transaction's direction: "500" on a debit means ₹500 out. */
interface Piece {
  key: number;
  kind: PieceKind;
  categoryId: string | null;
  amount: string;
  note: string;
  purchase: LinkedPurchase | null;
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
  const kinds: PieceKind[] = direction > 0 ? [...KINDS, "REFUND"] : KINDS;

  // Saved links only carry the purchase's name and date; the candidates add what is left to refund and its categories.
  const hasSavedLinks = transaction.allocations.some((allocation) => allocation.refundOf !== null);
  useEffect(() => {
    if (!hasSavedLinks) return;
    const controller = new AbortController();
    listRefundCandidates(transaction.id, "", controller.signal)
      .then((candidates) => {
        const byId = new Map(candidates.map((candidate) => [candidate.id, candidate]));
        setPieces((current) =>
          current.map((piece) => {
            const candidate = piece.purchase ? byId.get(piece.purchase.id) : undefined;
            return candidate ? { ...piece, purchase: toLinkedPurchase(candidate) } : piece;
          }),
        );
      })
      .catch(() => {
        // Without the details the links still save; the server checks them.
      });
    return () => controller.abort();
  }, [transaction.id, hasSavedLinks]);

  const parsed = pieces.map((piece) => parsePaise(piece.amount));
  const allocatedPaise = parsed.reduce<number>((total, paise) => total + (paise ?? 0), 0);
  const remainingPaise = totalPaise - allocatedPaise;
  const everyAmountValid = parsed.every((paise) => paise !== null && paise !== 0);
  const problems = pieces.map((piece, index) => refundProblem(piece, parsed[index] ?? null));
  const canSave =
    everyAmountValid && remainingPaise === 0 && problems.every((problem) => problem === null) && submit.status !== "saving";

  function update(key: number, changes: Partial<Piece>) {
    setPieces((current) => current.map((piece) => (piece.key === key ? { ...piece, ...changes } : piece)));
  }

  function linkPurchase(piece: Piece, candidate: RefundCandidate) {
    const amountPaise = parsePaise(piece.amount);
    const refundablePaise = Math.round(candidate.refundable * 100);
    update(piece.key, {
      purchase: toLinkedPurchase(candidate),
      categoryId: candidate.categoryIds[0] ?? null,
      amount: amountPaise !== null && amountPaise > refundablePaise ? toRupees(refundablePaise) : piece.amount,
    });
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
        purchase: null,
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
        kind: piece.kind === "REFUND" ? "EXPENSE" : piece.kind,
        categoryId: HAS_CATEGORIES[piece.kind] ? piece.categoryId : null,
        amount: ((parsed[index] ?? 0) * direction) / 100,
        note: piece.note.trim() === "" ? null : piece.note.trim(),
        refundOf: piece.kind === "REFUND" ? (piece.purchase?.id ?? null) : null,
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

      {transaction.refundedAmount > 0 && (
        <p className="form__hint">
          {formatMoney(transaction.refundedAmount)} of this purchase has been refunded, so it must stay an expense of at
          least that much.
        </p>
      )}

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
                    update(piece.key, { kind: event.target.value as PieceKind, categoryId: null, purchase: null })
                  }
                >
                  {kinds.map((kind) => (
                    <option key={kind} value={kind}>
                      {PIECE_LABELS[kind]}
                    </option>
                  ))}
                </select>
              </label>

              {piece.kind === "REFUND" && piece.purchase === null ? (
                <div className="field">
                  <span className="field__label">Category</span>
                  <p className="piece__hint">Comes from the purchase you pick below.</p>
                </div>
              ) : HAS_CATEGORIES[piece.kind] ? (
                <label className="field">
                  <span className="field__label">Category</span>
                  <CategorySelect
                    categories={categories}
                    kind={piece.kind === "REFUND" ? "EXPENSE" : piece.kind}
                    allowed={piece.kind === "REFUND" ? (piece.purchase?.categoryIds ?? null) : null}
                    value={piece.categoryId}
                    onChange={(categoryId) => update(piece.key, { categoryId })}
                  />
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
                  aria-invalid={parsed[index] === null || parsed[index] === 0 || problems[index] !== null}
                  required
                />
              </label>

              {piece.kind === "REFUND" && (
                <RefundPicker
                  transactionId={transaction.id}
                  purchase={piece.purchase}
                  problem={problems[index] ?? null}
                  onPick={(candidate) => linkPurchase(piece, candidate)}
                  onClear={() => update(piece.key, { purchase: null, categoryId: null })}
                />
              )}

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

/** Categories of one kind. `allowed`, when known, narrows it to a refunded purchase's categories. */
export function CategorySelect({
  categories,
  kind,
  allowed,
  value,
  onChange,
}: {
  categories: CategoryNode[];
  kind: AllocationKind;
  allowed: string[] | null;
  value: string | null;
  onChange: (categoryId: string | null) => void;
}) {
  const isAllowed = (id: string) => allowed === null || allowed.includes(id);
  return (
    <select
      className="input"
      value={value ?? ""}
      onChange={(event) => onChange(event.target.value === "" ? null : event.target.value)}
    >
      {(allowed === null || allowed.length === 0) && <option value="">No category</option>}
      {categories
        .filter((parent) => parent.kind === kind)
        .map((parent) => {
          const children = parent.children.filter((child) => isAllowed(child.id));
          if (children.length === 0) {
            return isAllowed(parent.id) ? (
              <option key={parent.id} value={parent.id}>
                {parent.name}
              </option>
            ) : null;
          }
          return (
            <optgroup key={parent.id} label={parent.name}>
              {isAllowed(parent.id) && <option value={parent.id}>{parent.name} (general)</option>}
              {children.map((child) => (
                <option key={child.id} value={child.id}>
                  {child.name}
                </option>
              ))}
            </optgroup>
          );
        })}
    </select>
  );
}

/** Shows the linked purchase, or a searchable list of purchases this refund could belong to. */
function RefundPicker({
  transactionId,
  purchase,
  problem,
  onPick,
  onClear,
}: {
  transactionId: string;
  purchase: LinkedPurchase | null;
  problem: string | null;
  onPick: (candidate: RefundCandidate) => void;
  onClear: () => void;
}) {
  const [search, setSearch] = useState("");
  const [candidates, setCandidates] = useState<RefundCandidate[] | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    if (purchase !== null) return;
    const controller = new AbortController();
    // Waits for a pause in typing so each keystroke doesn't send a request.
    const timer = window.setTimeout(() => {
      listRefundCandidates(transactionId, search, controller.signal)
        .then((found) => {
          setCandidates(found);
          setError(null);
        })
        .catch((reason: unknown) => {
          if (controller.signal.aborted) return;
          setError(reason instanceof Error ? reason.message : "Unknown error");
        });
    }, 250);
    return () => {
      window.clearTimeout(timer);
      controller.abort();
    };
  }, [transactionId, search, purchase]);

  if (purchase !== null) {
    return (
      <div className="field refund-picker">
        <span className="field__label">Refund of</span>
        <div className="refund-picker__linked">
          <div className="refund-picker__text">
            <p className="refund-picker__name" title={purchase.description}>
              {merchantName(purchase.description)}
            </p>
            <p className="refund-picker__meta">
              {formatLocalDate(purchase.transactionDate)}
              {purchase.refundable !== null && <> · {formatMoney(purchase.refundable)} left to refund</>}
            </p>
          </div>
          <button type="button" className="button button--ghost button--sm" onClick={onClear}>
            Change
          </button>
        </div>
        {problem !== null && <p className="refund-picker__problem">{problem}</p>}
      </div>
    );
  }

  return (
    <div className="field refund-picker">
      <span className="field__label">Refund of</span>
      <input
        className="input"
        type="search"
        value={search}
        onChange={(event) => setSearch(event.target.value)}
        placeholder="Search purchases from the last 120 days"
        aria-label="Search purchases"
      />
      {error !== null ? (
        <p className="refund-picker__problem">{error}</p>
      ) : candidates === null ? (
        <p className="refund-picker__empty">Loading purchases…</p>
      ) : candidates.length === 0 ? (
        <p className="refund-picker__empty">
          No classified expense with money left to refund{search.trim() !== "" ? " matches that" : ""}. Classify the
          purchase as an expense first, or save this as a plain expense instead.
        </p>
      ) : (
        <ul className="refund-picker__list">
          {candidates.map((candidate) => (
            <li key={candidate.id}>
              <button type="button" className="refund-picker__option" onClick={() => onPick(candidate)}>
                <span className="refund-picker__text">
                  <span className="refund-picker__name" title={candidate.description}>
                    {merchantName(candidate.description)}
                  </span>
                  <span className="refund-picker__meta">{formatLocalDate(candidate.transactionDate)}</span>
                </span>
                <span className="refund-picker__amounts num">
                  {formatMoney(candidate.refundable)}
                  {candidate.refundable < candidate.spent && (
                    <span className="refund-picker__meta"> of {formatMoney(candidate.spent)}</span>
                  )}
                </span>
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}

/** Why a refund piece can't be saved yet, or null. The server repeats these checks. */
function refundProblem(piece: Piece, paise: number | null): string | null {
  if (piece.kind !== "REFUND") return null;
  if (piece.purchase === null) return "Pick the purchase this refunds.";
  if (paise !== null && paise < 0) return "A refund can't be negative.";
  if (piece.purchase.refundable !== null && paise !== null && paise > Math.round(piece.purchase.refundable * 100)) {
    return `Only ${formatMoney(piece.purchase.refundable)} of this purchase is left to refund.`;
  }
  return null;
}

function toLinkedPurchase(candidate: RefundCandidate): LinkedPurchase {
  return {
    id: candidate.id,
    description: candidate.description,
    transactionDate: candidate.transactionDate,
    refundable: candidate.refundable,
    categoryIds: candidate.categoryIds,
  };
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
        purchase: null,
      },
    ];
  }
  const purchases = new Map(transaction.refundedPurchases.map((purchase) => [purchase.id, purchase]));
  return transaction.allocations.map((allocation, index) => {
    const linked = allocation.refundOf !== null ? purchases.get(allocation.refundOf) : undefined;
    return {
      key: index,
      kind: linked ? "REFUND" : allocation.kind,
      categoryId: allocation.categoryId,
      amount: toRupees(Math.round(allocation.amount * direction * 100)),
      note: allocation.note ?? "",
      purchase: linked ? { ...linked, refundable: null, categoryIds: null } : null,
    };
  });
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
