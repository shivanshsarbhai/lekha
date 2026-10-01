import { AccountList, AccountListSkeleton } from "./AccountList";
import { AddAccountForm } from "./AddAccountForm";
import { useAccounts } from "./useAccounts";

export function AccountsPage() {
  const { state, addAccount } = useAccounts();

  const accounts = state.status === "success" ? state.accounts : [];
  const banks = accounts.filter((a) => a.type === "BANK").length;
  const cards = accounts.filter((a) => a.type === "CREDIT_CARD").length;

  return (
    <section className="accounts">
      <div className="accounts__header">
        <div>
          <h1 className="page-title">Accounts</h1>
          <p className="page-subtitle">The bank accounts and credit cards Lekha tracks for you.</p>
        </div>
        {state.status === "success" && (
          <dl className="stats">
            <div className="stat">
              <dt>Bank accounts</dt>
              <dd>{banks}</dd>
            </div>
            <div className="stat">
              <dt>Credit cards</dt>
              <dd>{cards}</dd>
            </div>
          </dl>
        )}
      </div>

      <div className="accounts__layout">
        <div className="accounts__list">
          {state.status === "loading" && <AccountListSkeleton />}
          {state.status === "error" && (
            <div className="alert alert--error">Could not load accounts: {state.message}</div>
          )}
          {state.status === "success" && <AccountList accounts={state.accounts} />}
        </div>
        <AddAccountForm onCreated={addAccount} />
      </div>
    </section>
  );
}
