import { useState } from "react";
import { updateTransaction, type Transaction } from "../../api/transactions";
import { Icon } from "../../components/Icon";
import { formatLocalDate, formatMoney } from "../../lib/format";

type SubmitState = { status: "idle" } | { status: "saving" } | { status: "error"; message: string };

type Direction = "out" | "in";

interface EditTransactionProps {
  transaction: Transaction;
  onSaved: (transaction: Transaction) => void;
  onCancel: () => void;
}

/** The amount is entered as a positive number plus a direction, so a sign can't be lost while typing. */
export function EditTransaction({ transaction, onSaved, onCancel }: EditTransactionProps) {
  const [date, setDate] = useState(transaction.transactionDate);
  const [description, setDescription] = useState(transaction.description);
  const [direction, setDirection] = useState<Direction>(transaction.amount < 0 ? "out" : "in");
  const [amount, setAmount] = useState(Math.abs(transaction.amount).toFixed(2));
  const [submit, setSubmit] = useState<SubmitState>({ status: "idle" });

  const parsed = Number(amount);
  const signed = direction === "out" ? -parsed : parsed;
  const amountChanged = Number.isFinite(parsed) && Math.round(signed * 100) !== Math.round(transaction.amount * 100);
  const originalDate = transaction.metadata["originalTransactionDate"];
  const originalAmount = transaction.metadata["originalAmount"];

  async function handleSubmit(event: { preventDefault(): void }) {
    event.preventDefault();
    setSubmit({ status: "saving" });
    try {
      onSaved(await updateTransaction(transaction.id, { transactionDate: date, description, amount: signed }));
    } catch (error: unknown) {
      setSubmit({ status: "error", message: error instanceof Error ? error.message : "Unknown error" });
    }
  }

  return (
    <form className="form" onSubmit={handleSubmit}>
      <label className="field">
        <span className="field__label">Description</span>
        <input
          className="input"
          value={description}
          onChange={(e) => setDescription(e.target.value)}
          required
          autoFocus
        />
      </label>

      <div className="form__row">
        <label className="field">
          <span className="field__label">Date</span>
          <input className="input" type="date" value={date} onChange={(e) => setDate(e.target.value)} required />
        </label>

        <label className="field">
          <span className="field__label">Amount (₹)</span>
          <input
            className="input mono"
            value={amount}
            onChange={(e) => setAmount(e.target.value)}
            inputMode="decimal"
            pattern="[0-9]+(\.[0-9]{1,2})?"
            title="A positive amount with at most 2 decimals"
            required
          />
        </label>
      </div>

      <fieldset className="field">
        <legend className="field__label">Direction</legend>
        <div className="segmented">
          {(["out", "in"] as const).map((option) => (
            <label
              key={option}
              className={`segmented__option${direction === option ? " segmented__option--active" : ""}`}
            >
              <input
                type="radio"
                name="direction"
                value={option}
                checked={direction === option}
                onChange={() => setDirection(option)}
              />
              <Icon name={option === "out" ? "arrowUp" : "arrowDown"} size={16} />
              {option === "out" ? "Money out" : "Money in"}
            </label>
          ))}
        </div>
      </fieldset>

      {(originalDate !== undefined || originalAmount !== undefined) && (
        <p className="form__hint">
          <Icon name="info" size={14} /> The bank recorded this as{" "}
          {[
            originalDate !== undefined ? formatLocalDate(originalDate) : null,
            originalAmount !== undefined ? formatMoney(Number(originalAmount)) : null,
          ]
            .filter((part) => part !== null)
            .join(", ")}
          . Re-importing its statement won't add it again.
        </p>
      )}

      {amountChanged && transaction.allocations.length > 0 && (
        <p className="form__hint">
          <Icon name="alert" size={14} /> Changing the amount clears this transaction's classification, so you'll
          classify it again.
        </p>
      )}

      {submit.status === "error" && (
        <p className="form__error" role="alert">
          {submit.message}
        </p>
      )}

      <div className="dialog__actions">
        <button type="button" className="button button--ghost" onClick={onCancel}>
          Cancel
        </button>
        <button className="button" type="submit" disabled={submit.status === "saving"}>
          {submit.status === "saving" ? "Saving…" : "Save changes"}
        </button>
      </div>
    </form>
  );
}
