import type { Account } from "../../api/accounts";
import { Icon } from "../../components/Icon";
import { formatInstant } from "../../lib/format";
import { INSTITUTION_LABELS, INSTITUTION_SHORT, TYPE_LABELS } from "./labels";

export interface LastImport {
  imported: number;
  skipped: number;
  at: number;
}

interface AccountListProps {
  accounts: Account[];
  lastImports: Record<string, LastImport>;
  onImport: (account: Account) => void;
  onAdd: () => void;
}

export function AccountList({ accounts, lastImports, onImport, onAdd }: AccountListProps) {
  if (accounts.length === 0) {
    return (
      <div className="empty">
        <span className="empty__icon">
          <Icon name="wallet" size={26} />
        </span>
        <p className="empty__title">No accounts yet</p>
        <p className="empty__text">
          Add a bank account or credit card, then import its statement to see where your money went.
        </p>
        <button type="button" className="button" onClick={onAdd}>
          <Icon name="plus" /> Add your first account
        </button>
      </div>
    );
  }

  return (
    <ul className="account-grid">
      {accounts.map((account) => (
        <AccountCard
          key={account.id}
          account={account}
          lastImport={lastImports[account.id]}
          onImport={() => onImport(account)}
        />
      ))}
    </ul>
  );
}

interface AccountCardProps {
  account: Account;
  lastImport: LastImport | undefined;
  onImport: () => void;
}

function AccountCard({ account, lastImport, onImport }: AccountCardProps) {
  return (
    <li className="account-card">
      <div className="account-card__top">
        <span className={`bank-badge bank-badge--${account.institution.toLowerCase()}`} aria-hidden="true">
          {INSTITUTION_SHORT[account.institution]}
        </span>
        <div className="account-card__heading">
          <p className="account-card__nickname">{account.nickname}</p>
          <p className="account-card__institution">{INSTITUTION_LABELS[account.institution]}</p>
        </div>
        <span className={`tag tag--${account.type === "BANK" ? "bank" : "card"}`}>
          <Icon name={account.type === "BANK" ? "bank" : "card"} size={13} />
          {TYPE_LABELS[account.type]}
        </span>
      </div>

      <dl className="account-card__facts">
        <div>
          <dt>Number</dt>
          <dd className="mono">{account.last4 !== null ? `•••• ${account.last4}` : "—"}</dd>
        </div>
        <div>
          <dt>Added</dt>
          <dd>{formatInstant(account.createdAt)}</dd>
        </div>
      </dl>

      <div className="account-card__footer">
        <span className="account-card__status">
          {lastImport ? (
            <>
              <span className="status-dot status-dot--ok" />
              {lastImport.imported} new
              {lastImport.skipped > 0 ? ` · ${lastImport.skipped} skipped` : ""} just now
            </>
          ) : (
            <>
              <span className="status-dot" />
              No statement imported this session
            </>
          )}
        </span>
        <button type="button" className="button button--secondary button--sm" onClick={onImport}>
          <Icon name="upload" size={16} /> Import statement
        </button>
      </div>
    </li>
  );
}

export function AccountListSkeleton() {
  return (
    <ul className="account-grid" aria-label="Loading accounts">
      {[0, 1, 2].map((i) => (
        <li key={i} className="account-card account-card--skeleton">
          <span className="skeleton skeleton--badge" />
          <span className="skeleton skeleton--line" />
          <span className="skeleton skeleton--line skeleton--short" />
        </li>
      ))}
    </ul>
  );
}
