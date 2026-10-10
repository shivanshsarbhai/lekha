import { useState } from "react";
import type { ListedAccount } from "../../api/accounts";
import type { StatementImport } from "../../api/statements";
import { Dialog } from "../../components/Dialog";
import { Icon } from "../../components/Icon";
import { ImportStatement } from "../statements/ImportStatement";
import { AccountForm } from "./AccountForm";
import { AccountList, AccountListSkeleton, type LastImport } from "./AccountList";
import { DeleteAccount } from "./DeleteAccount";
import { INSTITUTION_LABELS } from "./labels";
import { useAccounts } from "./useAccounts";

export function AccountsPage() {
  const { state, addAccount, replaceAccount, removeAccount, countImported } = useAccounts();
  const [adding, setAdding] = useState(false);
  const [editing, setEditing] = useState<ListedAccount | null>(null);
  const [deleting, setDeleting] = useState<ListedAccount | null>(null);
  const [importingInto, setImportingInto] = useState<ListedAccount | null>(null);
  const [lastImports, setLastImports] = useState<Record<string, LastImport>>({});

  const accounts = state.status === "success" ? state.accounts : [];
  const banks = accounts.filter((account) => account.type === "BANK").length;
  const cards = accounts.filter((account) => account.type === "CREDIT_CARD").length;

  function recordImport(account: ListedAccount, result: StatementImport) {
    setLastImports((current) => ({
      ...current,
      [account.id]: { imported: result.imported.length, skipped: result.skipped, at: Date.now() },
    }));
    countImported(account.id, result.imported.length);
  }

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <p className="page-header__eyebrow">Money sources</p>
          <h1 className="page-header__title">Accounts</h1>
          <p className="page-header__subtitle">
            The bank accounts and credit cards Lekha reads your statements from.
          </p>
        </div>
        <button type="button" className="button" onClick={() => setAdding(true)}>
          <Icon name="plus" /> Add account
        </button>
      </header>

      {state.status === "success" && accounts.length > 0 && (
        <dl className="metrics">
          <Metric icon="wallet" label="Total accounts" value={accounts.length} />
          <Metric icon="bank" label="Bank accounts" value={banks} />
          <Metric icon="card" label="Credit cards" value={cards} />
        </dl>
      )}

      {state.status === "loading" && <AccountListSkeleton />}
      {state.status === "error" && (
        <div className="callout callout--error" role="alert">
          <Icon name="alert" className="callout__icon" />
          <div>
            <p className="callout__title">Couldn't load your accounts</p>
            <p className="callout__text">{state.message}</p>
          </div>
        </div>
      )}
      {state.status === "success" && (
        <AccountList
          accounts={state.accounts}
          lastImports={lastImports}
          onImport={setImportingInto}
          onEdit={setEditing}
          onDelete={setDeleting}
          onAdd={() => setAdding(true)}
        />
      )}

      <Dialog
        open={adding}
        onClose={() => setAdding(false)}
        title="Add an account"
        description="Lekha only needs a name for it. Nothing here connects to your bank."
      >
        <AccountForm
          onSaved={(account) => {
            addAccount(account);
            setAdding(false);
          }}
          onCancel={() => setAdding(false)}
        />
      </Dialog>

      <Dialog open={editing !== null} onClose={() => setEditing(null)} title="Edit account">
        {editing && (
          <AccountForm
            account={editing}
            onSaved={(account) => {
              replaceAccount(account);
              setEditing(null);
            }}
            onCancel={() => setEditing(null)}
          />
        )}
      </Dialog>

      <Dialog open={deleting !== null} onClose={() => setDeleting(null)} title="Delete account?">
        {deleting && (
          <DeleteAccount
            account={deleting}
            onDeleted={() => {
              removeAccount(deleting.id);
              setDeleting(null);
            }}
            onCancel={() => setDeleting(null)}
          />
        )}
      </Dialog>

      <Dialog
        open={importingInto !== null}
        onClose={() => setImportingInto(null)}
        title={importingInto ? `Import into ${importingInto.nickname}` : "Import statement"}
        {...(importingInto
          ? { description: `${INSTITUTION_LABELS[importingInto.institution]} statement as a PDF` }
          : {})}
        size="lg"
      >
        {importingInto && (
          <ImportStatement
            account={importingInto}
            onImported={(result) => recordImport(importingInto, result)}
            onDone={() => setImportingInto(null)}
          />
        )}
      </Dialog>
    </div>
  );
}

function Metric({ icon, label, value }: { icon: "wallet" | "bank" | "card"; label: string; value: number }) {
  return (
    <div className="metric">
      <span className="metric__icon">
        <Icon name={icon} />
      </span>
      <div>
        <dt className="metric__label">{label}</dt>
        <dd className="metric__value">{value}</dd>
      </div>
    </div>
  );
}
