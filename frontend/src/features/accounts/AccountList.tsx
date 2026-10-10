import type { ListedAccount } from "../../api/accounts";
import { Icon } from "../../components/Icon";
import { formatInstant } from "../../lib/format";
import { INSTITUTION_LABELS, TYPE_LABELS } from "./labels";

export interface LastImport {
  imported: number;
  skipped: number;
  at: number;
}

interface AccountListProps {
  accounts: ListedAccount[];
  lastImports: Record<string, LastImport>;
  onImport: (account: ListedAccount) => void;
  onEdit: (account: ListedAccount) => void;
  onDelete: (account: ListedAccount) => void;
  onAdd: () => void;
}

export function AccountList({ accounts, lastImports, onImport, onEdit, onDelete, onAdd }: AccountListProps) {
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
          onEdit={() => onEdit(account)}
          onDelete={() => onDelete(account)}
        />
      ))}
    </ul>
  );
}

interface AccountCardProps {
  account: ListedAccount;
  lastImport: LastImport | undefined;
  onImport: () => void;
  onEdit: () => void;
  onDelete: () => void;
}

function AccountCard({ account, lastImport, onImport, onEdit, onDelete }: AccountCardProps) {
  return (
    <li className="account-card">
      <div className={`card-face card-face--${account.institution.toLowerCase()}`}>
        <div className="card-face__top">
          <span className="card-face__bank">{INSTITUTION_LABELS[account.institution]}</span>
          <span className="card-face__type">
            <Icon name={account.type === "BANK" ? "bank" : "card"} size={13} />
            {TYPE_LABELS[account.type]}
          </span>
        </div>
        {account.type === "CREDIT_CARD" && <span className="card-face__chip" aria-hidden="true" />}
        <div className="card-face__bottom">
          <p className="card-face__nickname">{account.nickname}</p>
          <span className="card-face__number mono">{account.last4 !== null ? `•••• ${account.last4}` : ""}</span>
        </div>
      </div>

      <p className="account-card__added">
        Added {formatInstant(account.createdAt)} · {account.transactionCount}{" "}
        {account.transactionCount === 1 ? "transaction" : "transactions"}
      </p>

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
        <div className="account-card__actions">
          <button type="button" className="icon-button" onClick={onEdit} aria-label={`Edit ${account.nickname}`} title="Edit">
            <Icon name="edit" size={16} />
          </button>
          <button
            type="button"
            className="icon-button icon-button--danger"
            onClick={onDelete}
            aria-label={`Delete ${account.nickname}`}
            title="Delete"
          >
            <Icon name="trash" size={16} />
          </button>
          <button type="button" className="button button--secondary button--sm" onClick={onImport}>
            <Icon name="upload" size={16} /> Import statement
          </button>
        </div>
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
