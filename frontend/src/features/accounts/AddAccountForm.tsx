import { useState } from "react";
import { ApiError } from "../../api/client";
import { createAccount, type Account, type AccountType, type Institution } from "../../api/accounts";
import { INSTITUTION_LABELS, INSTITUTIONS, TYPE_LABELS } from "./labels";

type SubmitState = { status: "idle" } | { status: "saving" } | { status: "error"; message: string };

export function AddAccountForm({ onCreated }: { onCreated: (account: Account) => void }) {
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
      setNickname("");
      setLast4("");
      setSubmit({ status: "idle" });
    } catch (error: unknown) {
      const message =
        error instanceof ApiError
          ? `The server rejected this account (HTTP ${error.status}). Is the nickname already used?`
          : "Could not reach the server. Is the backend running?";
      setSubmit({ status: "error", message });
    }
  }

  const saving = submit.status === "saving";

  return (
    <form className="panel form" onSubmit={handleSubmit}>
      <h2 className="panel__title">Add an account</h2>

      <label className="field">
        <span className="field__label">Nickname</span>
        <input
          className="field__input"
          value={nickname}
          onChange={(e) => setNickname(e.target.value)}
          placeholder="e.g. HDFC Salary"
          maxLength={60}
          required
        />
      </label>

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
              {TYPE_LABELS[option]}
            </label>
          ))}
        </div>
      </fieldset>

      <label className="field">
        <span className="field__label">Bank</span>
        <select
          className="field__input"
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
          Last 4 digits <span className="field__optional">optional</span>
        </span>
        <input
          className="field__input field__input--mono"
          value={last4}
          onChange={(e) => setLast4(e.target.value)}
          placeholder="1234"
          inputMode="numeric"
          pattern="[0-9]{4}"
          title="Exactly 4 digits"
          maxLength={4}
        />
      </label>

      {submit.status === "error" && (
        <p className="form__error" role="alert">
          {submit.message}
        </p>
      )}

      <button className="button" type="submit" disabled={saving}>
        {saving ? "Saving…" : "Add account"}
      </button>
    </form>
  );
}
