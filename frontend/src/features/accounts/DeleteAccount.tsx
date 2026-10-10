import { useState } from "react";
import { deleteAccount, type ListedAccount } from "../../api/accounts";

type SubmitState = { status: "idle" } | { status: "deleting" } | { status: "error"; message: string };

interface DeleteAccountProps {
  account: ListedAccount;
  onDeleted: () => void;
  onCancel: () => void;
}

/** There is no undo, so an account with transactions is only deleted after its nickname is typed back. */
export function DeleteAccount({ account, onDeleted, onCancel }: DeleteAccountProps) {
  const [typed, setTyped] = useState("");
  const [submit, setSubmit] = useState<SubmitState>({ status: "idle" });
  const count = account.transactionCount;
  const confirmed = count === 0 || typed.trim().toLowerCase() === account.nickname.toLowerCase();

  async function handleSubmit(event: { preventDefault(): void }) {
    event.preventDefault();
    setSubmit({ status: "deleting" });
    try {
      await deleteAccount(account.id);
      onDeleted();
    } catch (error: unknown) {
      setSubmit({ status: "error", message: error instanceof Error ? error.message : "Unknown error" });
    }
  }

  return (
    <form className="form" onSubmit={handleSubmit}>
      <p className="dialog__text">
        {count === 0 ? (
          <>
            <strong>{account.nickname}</strong> has no transactions, so nothing else is removed.
          </>
        ) : (
          <>
            This permanently deletes <strong>{account.nickname}</strong> and its{" "}
            <strong>
              {count} {count === 1 ? "transaction" : "transactions"}
            </strong>
            , including how you classified them. Re-importing its statements would bring the transactions back, but
            not the classifications.
          </>
        )}
      </p>

      {count > 0 && (
        <label className="field">
          <span className="field__label">Type “{account.nickname}” to confirm</span>
          <input className="input" value={typed} onChange={(e) => setTyped(e.target.value)} autoFocus />
        </label>
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
        <button className="button button--danger" type="submit" disabled={!confirmed || submit.status === "deleting"}>
          {submit.status === "deleting" ? "Deleting…" : "Delete account"}
        </button>
      </div>
    </form>
  );
}
