import { useRef, useState, type DragEvent } from "react";
import { Link } from "react-router";
import type { Account } from "../../api/accounts";
import type { StatementImport } from "../../api/statements";
import type { Transaction } from "../../api/transactions";
import { Icon } from "../../components/Icon";
import { formatFileSize, formatLocalDate, formatMoney, formatSignedMoney, sumMoney } from "../../lib/format";
import { merchantName } from "../../lib/merchant";
import { useStatementUpload } from "./useStatementUpload";

/** Matches spring.servlet.multipart.max-file-size, so oversized files fail fast without uploading. */
const MAX_FILE_BYTES = 10 * 1024 * 1024;

interface ImportStatementProps {
  account: Account;
  onImported: (result: StatementImport) => void;
  onDone: () => void;
}

export function ImportStatement({ account, onImported, onDone }: ImportStatementProps) {
  const { state, upload, reset } = useStatementUpload(account.id);
  const [fileError, setFileError] = useState<string | null>(null);

  async function handleFile(file: File) {
    if (!isPdf(file)) {
      setFileError(`"${file.name}" is not a PDF. Download the statement from your bank as a PDF.`);
      return;
    }
    if (file.size > MAX_FILE_BYTES) {
      setFileError(`"${file.name}" is ${formatFileSize(file.size)}. Statements up to 10 MB are supported.`);
      return;
    }
    setFileError(null);
    const result = await upload(file);
    if (result) onImported(result);
  }

  switch (state.status) {
    case "idle":
      return <Dropzone onFile={handleFile} error={fileError} />;

    case "uploading":
      return (
        <div className="import-progress" role="status">
          <span className="spinner" aria-hidden="true" />
          <div>
            <p className="import-progress__title">Reading {state.fileName}</p>
            <p className="import-progress__text">Checking every row against the statement summary…</p>
          </div>
        </div>
      );

    case "locked":
      return (
        <PasswordPrompt
          fileName={state.file.name}
          message={state.message}
          wrongPassword={state.wrongPassword}
          onSubmit={async (password) => {
            const result = await upload(state.file, password);
            if (result) onImported(result);
          }}
          onCancel={reset}
        />
      );

    case "failed":
      return (
        <div className="import-failed">
          <div className="callout callout--error" role="alert">
            <Icon name="alert" className="callout__icon" />
            <div>
              <p className="callout__title">We couldn't import {state.fileName}</p>
              <p className="callout__text">{state.message}</p>
            </div>
          </div>
          <p className="import-failed__hint">Nothing was saved. Your existing transactions are unchanged.</p>
          <div className="dialog__actions">
            <button type="button" className="button button--ghost" onClick={onDone}>
              Close
            </button>
            <button type="button" className="button" onClick={reset}>
              <Icon name="refresh" /> Try another file
            </button>
          </div>
        </div>
      );

    case "done":
      return (
        <ImportResult
          accountId={account.id}
          result={state.result}
          fileName={state.fileName}
          onAnother={reset}
          onDone={onDone}
        />
      );
  }
}

function Dropzone({ onFile, error }: { onFile: (file: File) => void; error: string | null }) {
  const input = useRef<HTMLInputElement>(null);
  const [dragging, setDragging] = useState(false);

  function handleDrop(event: DragEvent) {
    event.preventDefault();
    setDragging(false);
    const file = event.dataTransfer.files[0];
    if (file) onFile(file);
  }

  return (
    <div className="dropzone-wrap">
      <button
        type="button"
        className={`dropzone${dragging ? " dropzone--active" : ""}`}
        onClick={() => input.current?.click()}
        onDragOver={(event) => {
          event.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => setDragging(false)}
        onDrop={handleDrop}
      >
        <span className="dropzone__icon">
          <Icon name="upload" size={22} />
        </span>
        <span className="dropzone__title">Drop your statement PDF here</span>
        <span className="dropzone__text">or click to browse · PDF up to 10 MB</span>
      </button>
      <input
        ref={input}
        type="file"
        accept="application/pdf,.pdf"
        hidden
        onChange={(event) => {
          const file = event.target.files?.[0];
          event.target.value = "";
          if (file) onFile(file);
        }}
      />
      {error && (
        <p className="form__error" role="alert">
          {error}
        </p>
      )}
      <ul className="assurances">
        <li>
          <Icon name="check" size={16} /> Every balance is checked against the bank's own totals
        </li>
        <li>
          <Icon name="check" size={16} /> Re-uploading an overlapping statement only adds new rows
        </li>
        <li>
          <Icon name="check" size={16} /> The file is read in memory and never stored
        </li>
      </ul>
    </div>
  );
}

/** Asks for a locked PDF's password. It lives only in this form's state and is gone once the dialog moves on. */
function PasswordPrompt({
  fileName,
  message,
  wrongPassword,
  onSubmit,
  onCancel,
}: {
  fileName: string;
  message: string;
  wrongPassword: boolean;
  onSubmit: (password: string) => Promise<void>;
  onCancel: () => void;
}) {
  const [password, setPassword] = useState("");

  return (
    <form
      className="form"
      onSubmit={(event) => {
        event.preventDefault();
        if (password !== "") void onSubmit(password);
      }}
    >
      <div className={`callout ${wrongPassword ? "callout--error" : "callout--info"}`} role={wrongPassword ? "alert" : undefined}>
        <Icon name={wrongPassword ? "alert" : "file"} className="callout__icon" />
        <div>
          <p className="callout__title">{fileName} is locked</p>
          <p className="callout__text">{message}</p>
        </div>
      </div>
      <label className="field">
        <span className="field__label">PDF password</span>
        <input
          className="input"
          type="password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          autoComplete="off"
          autoFocus
          required
        />
        <span className="form__hint">
          Often a mix of your name and date of birth; your bank's statement email says which. It's used once to open the
          file and never stored.
        </span>
      </label>
      <div className="dialog__actions">
        <button type="button" className="button button--ghost" onClick={onCancel}>
          Choose another file
        </button>
        <button type="submit" className="button" disabled={password === ""}>
          Unlock and import
        </button>
      </div>
    </form>
  );
}

interface ImportResultProps {
  accountId: string;
  result: StatementImport;
  fileName: string;
  onAnother: () => void;
  onDone: () => void;
}

function ImportResult({ accountId, result, fileName, onAnother, onDone }: ImportResultProps) {
  const amounts = result.imported.map((transaction) => transaction.amount);
  const moneyIn = sumMoney(amounts.filter((amount) => amount > 0));
  const moneyOut = sumMoney(amounts.filter((amount) => amount < 0));
  const nothingNew = result.imported.length === 0;
  const latestMonth = result.imported
    .map((transaction) => transaction.transactionDate.slice(0, 7))
    .reduce<string | null>((latest, month) => (latest === null || month > latest ? month : latest), null);

  return (
    <div className="import-result">
      <div className={`callout ${nothingNew ? "callout--info" : "callout--success"}`}>
        <Icon name={nothingNew ? "file" : "check"} className="callout__icon" />
        <div>
          <p className="callout__title">
            {nothingNew ? "Already up to date" : `Imported ${plural(result.imported.length, "transaction")}`}
          </p>
          <p className="callout__text">
            {nothingNew
              ? `Every transaction in ${fileName} was imported before.`
              : `From ${fileName}${result.skipped > 0 ? `, skipping ${result.skipped} already imported` : ""}.`}
          </p>
        </div>
      </div>

      <dl className="summary-tiles">
        <div className="summary-tile">
          <dt>New</dt>
          <dd>{result.imported.length}</dd>
        </div>
        <div className="summary-tile">
          <dt>Already imported</dt>
          <dd>{result.skipped}</dd>
        </div>
        <div className="summary-tile summary-tile--in">
          <dt>
            <Icon name="arrowDown" size={14} /> Money in
          </dt>
          <dd>{formatMoney(moneyIn)}</dd>
        </div>
        <div className="summary-tile summary-tile--out">
          <dt>
            <Icon name="arrowUp" size={14} /> Money out
          </dt>
          <dd>{formatMoney(Math.abs(moneyOut))}</dd>
        </div>
      </dl>

      {!nothingNew && <TransactionTable transactions={result.imported} />}

      <div className="dialog__actions">
        <button type="button" className="button button--ghost" onClick={onAnother}>
          <Icon name="upload" /> Import another
        </button>
        {latestMonth !== null ? (
          <Link className="button" to={`/transactions?account=${accountId}&month=${latestMonth}`}>
            View in Transactions
          </Link>
        ) : (
          <button type="button" className="button" onClick={onDone}>
            Done
          </button>
        )}
      </div>
    </div>
  );
}

function TransactionTable({ transactions }: { transactions: Transaction[] }) {
  return (
    <div className="table-wrap">
      <table className="table">
        <thead>
          <tr>
            <th scope="col">Date</th>
            <th scope="col">Description</th>
            <th scope="col" className="table__num">
              Amount
            </th>
            <th scope="col" className="table__num">
              Balance
            </th>
          </tr>
        </thead>
        <tbody>
          {transactions.map((transaction) => (
            <tr key={transaction.id}>
              <td className="table__date">{formatLocalDate(transaction.transactionDate)}</td>
              <td>
                <span className="table__narration" title={transaction.description}>
                  {merchantName(transaction.description)}
                </span>
              </td>
              <td className={`table__num amount ${transaction.amount < 0 ? "amount--out" : "amount--in"}`}>
                {formatSignedMoney(transaction.amount)}
              </td>
              <td className="table__num table__muted">
                {transaction.balanceAfter !== null ? formatMoney(transaction.balanceAfter) : "—"}
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

function isPdf(file: File): boolean {
  return file.type === "application/pdf" || file.name.toLowerCase().endsWith(".pdf");
}

function plural(count: number, noun: string): string {
  return `${count} ${noun}${count === 1 ? "" : "s"}`;
}
