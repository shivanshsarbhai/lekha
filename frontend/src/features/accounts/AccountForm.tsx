import { useState } from "react";
import {
  createAccount,
  updateAccount,
  type Account,
  type AccountType,
  type Institution,
  type ListedAccount,
} from "../../api/accounts";
import { Icon } from "../../components/Icon";
import { INSTITUTION_LABELS, INSTITUTIONS, TYPE_LABELS } from "./labels";

type SubmitState = { status: "idle" } | { status: "saving" } | { status: "error"; message: string };

interface AccountFormProps {
  /** The account to edit; without it the form adds a new one. */
  account?: ListedAccount;
  onSaved: (account: Account) => void;
  onCancel: () => void;
}

export function AccountForm({ account, onSaved, onCancel }: AccountFormProps) {
  const [nickname, setNickname] = useState(account?.nickname ?? "");
  const [type, setType] = useState<AccountType>(account?.type ?? "BANK");
  const [institution, setInstitution] = useState<Institution>(account?.institution ?? "HDFC");
  const [last4, setLast4] = useState(account?.last4 ?? "");
  const [submit, setSubmit] = useState<SubmitState>({ status: "idle" });

  /** Mirrors the server rule: statements already read as one bank's format pin the bank and type. */
  const kindLocked = account !== undefined && account.transactionCount > 0;

  async function handleSubmit(event: { preventDefault(): void }) {
    event.preventDefault();
    setSubmit({ status: "saving" });
    const input = { nickname, type, institution, last4: last4.trim() === "" ? null : last4.trim() };
    try {
      onSaved(account ? await updateAccount(account.id, input) : await createAccount(input));
    } catch (error: unknown) {
      setSubmit({ status: "error", message: error instanceof Error ? error.message : "Unknown error" });
    }
  }

  const saving = submit.status === "saving";

  return (
    <form className="form" onSubmit={handleSubmit}>
      <fieldset className="field" disabled={kindLocked}>
        <legend className="field__label">Type</legend>
        <div className="segmented">
          {(Object.keys(TYPE_LABELS) as AccountType[]).map((option) => (
            <label key={option} className={`segmented__option${type === option ? " segmented__option--active" : ""}`}>
              <input
                type="radio"
                name="type"
                value={option}
                checked={type === option}
                onChange={() => setType(option)}
              />
              <Icon name={option === "BANK" ? "bank" : "card"} size={16} />
              {TYPE_LABELS[option]}
            </label>
          ))}
        </div>
      </fieldset>

      <label className="field">
        <span className="field__label">Nickname</span>
        <input
          className="input"
          value={nickname}
          onChange={(e) => setNickname(e.target.value)}
          placeholder={type === "BANK" ? "e.g. HDFC Salary" : "e.g. Scapia Card"}
          maxLength={60}
          required
          autoFocus
        />
      </label>

      <div className="form__row">
        <label className="field">
          <span className="field__label">Bank</span>
          <select
            className="input"
            value={institution}
            onChange={(e) => setInstitution(e.target.value as Institution)}
            disabled={kindLocked}
          >
            {INSTITUTIONS.map((option) => (
              <option key={option} value={option}>
                {INSTITUTION_LABELS[option]}
              </option>
            ))}
          </select>
        </label>

        <label className="field">
          <span className="field__label">
            Last 4 digits <span className="field__optional">Optional</span>
          </span>
          <input
            className="input mono"
            value={last4}
            onChange={(e) => setLast4(e.target.value)}
            placeholder="1234"
            inputMode="numeric"
            pattern="[0-9]{4}"
            title="Exactly 4 digits"
            maxLength={4}
          />
        </label>
      </div>

      {kindLocked && (
        <p className="form__hint">
          <Icon name="info" size={14} /> The bank and type can't change once statements are imported. To change them,
          delete this account and add it again.
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
        <button className="button" type="submit" disabled={saving}>
          {saving ? "Saving…" : account ? "Save changes" : "Add account"}
        </button>
      </div>
    </form>
  );
}
