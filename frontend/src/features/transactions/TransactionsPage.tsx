import { useMemo } from "react";
import { Link, useSearchParams } from "react-router";
import type { Account } from "../../api/accounts";
import type { Transaction } from "../../api/transactions";
import { Icon } from "../../components/Icon";
import { formatLocalDate, formatMoney, formatSignedMoney } from "../../lib/format";
import { currentMonth, formatMonth, isMonth, monthRange, shiftMonth, type Month } from "../../lib/month";
import { useAccounts } from "../accounts/useAccounts";
import { MODE_LABELS } from "./labels";
import { useTransactions } from "./useTransactions";

export function TransactionsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const monthParam = searchParams.get("month");
  const month: Month = isMonth(monthParam) ? monthParam : currentMonth();
  const accountId = searchParams.get("account");

  const { from, to } = monthRange(month);
  const state = useTransactions({ accountId, from, to });
  const { state: accountsState } = useAccounts();
  const accounts = accountsState.status === "success" ? accountsState.accounts : [];
  const accountsById = useMemo(() => new Map(accounts.map((account) => [account.id, account])), [accounts]);

  function updateFilter(changes: { month?: Month; account?: string | null }) {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      if (changes.month !== undefined) next.set("month", changes.month);
      if (changes.account !== undefined) {
        if (changes.account === null) next.delete("account");
        else next.set("account", changes.account);
      }
      return next;
    });
  }

  const selectedAccount = accountId !== null ? accountsById.get(accountId) : undefined;

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <p className="page-header__eyebrow">Ledger</p>
          <h1 className="page-header__title">Transactions</h1>
          <p className="page-header__subtitle">
            {selectedAccount ? selectedAccount.nickname : "All accounts"} · {formatMonth(month)}
          </p>
        </div>
      </header>

      <div className="toolbar">
        <div className="month-picker" role="group" aria-label="Month">
          <button
            type="button"
            className="icon-button"
            aria-label="Previous month"
            onClick={() => updateFilter({ month: shiftMonth(month, -1) })}
          >
            <Icon name="chevronLeft" />
          </button>
          <input
            type="month"
            className="month-picker__input"
            value={month}
            onChange={(event) => {
              if (isMonth(event.target.value)) updateFilter({ month: event.target.value });
            }}
            aria-label="Choose month"
          />
          <button
            type="button"
            className="icon-button"
            aria-label="Next month"
            onClick={() => updateFilter({ month: shiftMonth(month, 1) })}
          >
            <Icon name="chevronRight" />
          </button>
        </div>

        <label className="toolbar__select">
          <span className="sr-only">Account</span>
          <select
            className="input"
            value={accountId ?? ""}
            onChange={(event) => updateFilter({ account: event.target.value === "" ? null : event.target.value })}
          >
            <option value="">All accounts</option>
            {accounts.map((account) => (
              <option key={account.id} value={account.id}>
                {account.nickname}
              </option>
            ))}
          </select>
        </label>

        {month !== currentMonth() && (
          <button type="button" className="button button--ghost button--sm" onClick={() => updateFilter({ month: currentMonth() })}>
            This month
          </button>
        )}
      </div>

      {state.status === "error" ? (
        <div className="callout callout--error" role="alert">
          <Icon name="alert" className="callout__icon" />
          <div>
            <p className="callout__title">Couldn't load transactions</p>
            <p className="callout__text">{state.message}</p>
            {accountId !== null && (
              <button type="button" className="link-button" onClick={() => updateFilter({ account: null })}>
                Show all accounts instead
              </button>
            )}
          </div>
        </div>
      ) : (
        <>
          <dl className="metrics metrics--four">
            <Total label="Cash in" icon="arrowDown" tone="in" value={state.status === "success" ? state.data.moneyIn : null} />
            <Total
              label="Cash out"
              icon="arrowUp"
              tone="out"
              value={state.status === "success" ? Math.abs(state.data.moneyOut) : null}
            />
            <Total label="Net" icon="wallet" tone="neutral" value={state.status === "success" ? state.data.net : null} signed />
            <div className="metric">
              <span className="metric__icon">
                <Icon name="list" />
              </span>
              <div>
                <dt className="metric__label">Transactions</dt>
                <dd className="metric__value">
                  {state.status === "success" ? state.data.transactions.length : <span className="skeleton skeleton--value" />}
                </dd>
              </div>
            </div>
          </dl>
          <p className="footnote">
            <Icon name="info" size={14} /> Cash flow counts every movement, including transfers between your own
            accounts and investments. True spending arrives with classification.
          </p>

          {state.status === "loading" && <LedgerSkeleton />}
          {state.status === "success" &&
            (state.data.transactions.length === 0 ? (
              <div className="empty">
                <span className="empty__icon">
                  <Icon name="list" size={26} />
                </span>
                <p className="empty__title">No transactions in {formatMonth(month)}</p>
                <p className="empty__text">
                  Import a statement that covers this month, or pick another month above.
                </p>
                <Link className="button" to="/accounts">
                  <Icon name="upload" /> Import a statement
                </Link>
              </div>
            ) : (
              <Ledger transactions={state.data.transactions} accountsById={accountsById} showAccount={accountId === null} />
            ))}
        </>
      )}
    </div>
  );
}

interface TotalProps {
  label: string;
  icon: "arrowDown" | "arrowUp" | "wallet";
  tone: "in" | "out" | "neutral";
  value: number | null;
  signed?: boolean;
}

function Total({ label, icon, tone, value, signed = false }: TotalProps) {
  return (
    <div className={`metric metric--${tone}`}>
      <span className="metric__icon">
        <Icon name={icon} />
      </span>
      <div>
        <dt className="metric__label">{label}</dt>
        <dd className="metric__value">
          {value === null ? (
            <span className="skeleton skeleton--value" />
          ) : signed ? (
            formatSignedMoney(value)
          ) : (
            formatMoney(value)
          )}
        </dd>
      </div>
    </div>
  );
}

interface LedgerProps {
  transactions: Transaction[];
  accountsById: Map<string, Account>;
  showAccount: boolean;
}

function Ledger({ transactions, accountsById, showAccount }: LedgerProps) {
  const groups = groupByDate(transactions);

  return (
    <div className="ledger">
      <table className="table table--ledger">
        <thead>
          <tr>
            <th scope="col">Description</th>
            {showAccount && <th scope="col">Account</th>}
            <th scope="col" className="table__num">
              Amount
            </th>
            <th scope="col" className="table__num">
              Balance
            </th>
          </tr>
        </thead>
        {groups.map(({ date, rows }) => (
          <tbody key={date}>
            <tr className="table__group">
              <th scope="rowgroup" colSpan={showAccount ? 4 : 3}>
                {formatLocalDate(date)}
              </th>
            </tr>
            {rows.map((transaction) => (
              <tr key={transaction.id}>
                <td>
                  <div className="table__description">
                    {transaction.paymentMode && (
                      <span className={`mode mode--${transaction.paymentMode.toLowerCase()}`}>
                        {MODE_LABELS[transaction.paymentMode]}
                      </span>
                    )}
                    <span className="table__narration" title={transaction.description}>
                      {transaction.description}
                    </span>
                  </div>
                </td>
                {showAccount && (
                  <td className="table__muted table__account">
                    {accountsById.get(transaction.accountId)?.nickname ?? "—"}
                  </td>
                )}
                <td className={`table__num amount ${transaction.amount < 0 ? "amount--out" : "amount--in"}`}>
                  {formatSignedMoney(transaction.amount)}
                </td>
                <td className="table__num table__muted">
                  {transaction.balanceAfter !== null ? formatMoney(transaction.balanceAfter) : "—"}
                </td>
              </tr>
            ))}
          </tbody>
        ))}
      </table>
    </div>
  );
}

function LedgerSkeleton() {
  return (
    <div className="ledger ledger--skeleton" aria-label="Loading transactions">
      {[0, 1, 2, 3, 4, 5].map((i) => (
        <div key={i} className="ledger__skeleton-row">
          <span className="skeleton skeleton--line" />
          <span className="skeleton skeleton--amount" />
        </div>
      ))}
    </div>
  );
}

function groupByDate(transactions: Transaction[]): { date: string; rows: Transaction[] }[] {
  const groups: { date: string; rows: Transaction[] }[] = [];
  for (const transaction of transactions) {
    const last = groups.at(-1);
    if (last && last.date === transaction.transactionDate) last.rows.push(transaction);
    else groups.push({ date: transaction.transactionDate, rows: [transaction] });
  }
  return groups;
}
