import { useState } from "react";
import { createAccount, type Account, type AccountType, type Institution } from "../../api/accounts";
import { Icon } from "../../components/Icon";
import { INSTITUTION_LABELS, INSTITUTIONS, TYPE_LABELS } from "./labels";

type SubmitState = { status: "idle" } | { status: "saving" } | { status: "error"; message: string };

interface AddAccountFormProps {
  onCreated: (account: Account) => void;
  onCancel: () => void;
}

export function AddAccountForm({ onCreated, onCancel }: AddAccountFormProps) {
  const [nickname, setNickname] = useState("");
  const [type, setType] = useState<AccountType>("BANK");
  const [institution, setInstitution] = useState<Institution>("HDFC");
  const [last4, setLast4] = useState("");
  const [submit, setSubmit] = useState<SubmitState>({ status: "idle" });

  async function handleSubmit(event: { preventDefault(): void }) {
    event.preventDefault();
    setSubmit({ status: "saving" });
    try {
      const created = await createAccount({
        nickname,
        type,
        institution,
        last4: last4.trim() === "" ? null : last4.trim(),
      });
      onCreated(created);
    } catch (error: unknown) {
      setSubmit({ status: "error", message: error instanceof Error ? error.message : "Unknown error" });
    }
  }

  const saving = submit.status === "saving";

  return (
    <form className="form" onSubmit={handleSubmit}>
      <fieldset className="field">
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
          {saving ? "Saving…" : "Add account"}
        </button>
      </div>
    </form>
  );
}
