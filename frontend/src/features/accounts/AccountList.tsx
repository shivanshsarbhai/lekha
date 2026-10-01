import type { Account } from "../../api/accounts";
import { INSTITUTION_LABELS, INSTITUTION_SHORT, TYPE_LABELS } from "./labels";

const addedOn = new Intl.DateTimeFormat("en-IN", { day: "numeric", month: "short", year: "numeric" });

export function AccountList({ accounts }: { accounts: Account[] }) {
  if (accounts.length === 0) {
    return (
      <div className="empty">
        <div className="empty__icon" aria-hidden="true">
          ₹
        </div>
        <p className="empty__title">No accounts yet</p>
        <p className="empty__text">Add your bank accounts and credit cards to start tracking where your money goes.</p>
      </div>
    );
  }

  return (
    <ul className="account-grid">
      {accounts.map((account) => (
        <li key={account.id} className={`account-card account-card--${account.type.toLowerCase()}`}>
          <div className="account-card__top">
            <span className={`bank-badge bank-badge--${account.institution.toLowerCase()}`} aria-hidden="true">
              {INSTITUTION_SHORT[account.institution]}
            </span>
            <span className="chip">{TYPE_LABELS[account.type]}</span>
          </div>
          <p className="account-card__nickname">{account.nickname}</p>
          <p className="account-card__institution">{INSTITUTION_LABELS[account.institution]}</p>
          <div className="account-card__bottom">
            <span className="account-card__number">
              {account.last4 !== null ? `•••• ${account.last4}` : "No number added"}
            </span>
            <span className="account-card__date">Added {addedOn.format(new Date(account.createdAt))}</span>
          </div>
        </li>
      ))}
    </ul>
  );
}

export function AccountListSkeleton() {
  return (
    <ul className="account-grid" aria-label="Loading accounts">
      {[0, 1].map((i) => (
        <li key={i} className="account-card account-card--skeleton" />
      ))}
    </ul>
  );
}
