import { useMemo, useState, type CSSProperties } from "react";
import { Link, useSearchParams } from "react-router";
import type { Account } from "../../api/accounts";
import type { Allocation, Transaction } from "../../api/transactions";
import { Dialog } from "../../components/Dialog";
import { AccountSelect, MonthPicker } from "../../components/Filters";
import { Icon } from "../../components/Icon";
import { formatMoney, formatSignedMoney, formatWholeMoney, sumMoney } from "../../lib/format";
import { hueOf } from "../../lib/hue";
import { merchantName } from "../../lib/merchant";
import { currentMonth, formatMonth, isMonth, monthRange, type Month } from "../../lib/month";
import { useAccounts } from "../accounts/useAccounts";
import { useCategories, type CategoryLabel } from "../categories/useCategories";
import { ClassifyTransaction } from "./ClassifyTransaction";
import { KIND_LABELS } from "./labels";
import { useTransactions } from "./useTransactions";

export function TransactionsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const monthParam = searchParams.get("month");
  const month: Month = isMonth(monthParam) ? monthParam : currentMonth();
  const accountId = searchParams.get("account");

  const onlyUnclassified = searchParams.get("unclassified") === "1";

  const { from, to } = monthRange(month);
  const { state, applyClassification } = useTransactions({ accountId, from, to });
  const [classifying, setClassifying] = useState<Transaction | null>(null);
  const { state: accountsState } = useAccounts();
  const accounts = accountsState.status === "success" ? accountsState.accounts : [];
  const accountsById = useMemo(() => new Map(accounts.map((account) => [account.id, account])), [accounts]);

  function updateFilter(changes: { month?: Month; account?: string | null; unclassified?: boolean }) {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      if (changes.month !== undefined) next.set("month", changes.month);
      if (changes.account !== undefined) {
        if (changes.account === null) next.delete("account");
        else next.set("account", changes.account);
      }
      if (changes.unclassified !== undefined) {
        if (changes.unclassified) next.set("unclassified", "1");
        else next.delete("unclassified");
      }
      return next;
    });
  }

  const selectedAccount = accountId !== null ? accountsById.get(accountId) : undefined;
  const data = state.status === "success" ? state.data : null;
  const { state: categoriesState, labels: categoryLabels } = useCategories();
  const categoryTree = categoriesState.status === "success" ? categoriesState.tree : [];
  const visible = data
    ? onlyUnclassified
      ? data.transactions.filter((transaction) => transaction.allocations.length === 0)
      : data.transactions
    : [];

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <p className="page-header__eyebrow">Transactions</p>
          <h1 className="page-header__title">{formatMonth(month)}</h1>
          <p className="page-header__subtitle">
            {selectedAccount ? selectedAccount.nickname : "All accounts"} · every rupee in and out
          </p>
        </div>
        <div className="toolbar">
          <MonthPicker month={month} onChange={(next) => updateFilter({ month: next })} />
          <AccountSelect accounts={accounts} value={accountId} onChange={(next) => updateFilter({ account: next })} />
          <label className={`toggle${onlyUnclassified ? " toggle--on" : ""}`}>
            <input
              type="checkbox"
              checked={onlyUnclassified}
              onChange={(event) => updateFilter({ unclassified: event.target.checked })}
            />
            Only unclassified
          </label>
        </div>
      </header>

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
            <Total label="Spent" icon="arrowUp" tone="out" value={data?.spending ?? null} />
            <Total label="Income" icon="arrowDown" tone="in" value={data?.income ?? null} />
            <Total label="Invested" icon="chart" tone="neutral" value={data?.invested ?? null} />
            <Total label="Lent" icon="split" tone="lent" value={data?.lent ?? null} />
            <div className={`metric ${data && data.unclassifiedCount > 0 ? "metric--attention" : ""}`}>
              <span className="metric__icon">
                <Icon name="list" />
              </span>
              <div>
                <dt className="metric__label">Unclassified</dt>
                <dd className="metric__value">
                  {data ? (
                    <>
                      {data.unclassifiedCount}
                      <span className="metric__of"> of {data.transactions.length}</span>
                    </>
                  ) : (
                    <span className="skeleton skeleton--value" />
                  )}
                </dd>
              </div>
            </div>
          </dl>
          {data && (
            <p className="footnote">
              <Icon name="info" size={14} /> Cash flow: {formatMoney(data.moneyIn)} in · {formatMoney(Math.abs(data.moneyOut))}{" "}
              out · {formatSignedMoney(data.net)} net. Transfers between your accounts count here but not above.
            </p>
          )}

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
              visible.length === 0 ? (
                <div className="empty">
                  <span className="empty__icon">
                    <Icon name="check" size={26} />
                  </span>
                  <p className="empty__title">Everything in {formatMonth(month)} is classified</p>
                  <p className="empty__text">Your spending totals for this month are complete.</p>
                  <button type="button" className="button button--secondary" onClick={() => updateFilter({ unclassified: false })}>
                    Show all transactions
                  </button>
                </div>
              ) : (
                <Ledger
                  transactions={visible}
                  accountsById={accountsById}
                  categoryLabels={categoryLabels}
                  showAccount={accountId === null}
                  onClassify={setClassifying}
                />
              )
            ))}
        </>
      )}

      <Dialog
        open={classifying !== null}
        onClose={() => setClassifying(null)}
        title={classifying && classifying.allocations.length > 0 ? "Edit classification" : "Classify transaction"}
        description="Split it into pieces if part was yours and part wasn't. The pieces must add up to the full amount."
        size="lg"
      >
        {classifying && (
          <ClassifyTransaction
            transaction={classifying}
            categories={categoryTree}
            onSaved={(allocations) => {
              applyClassification(classifying.id, allocations);
              setClassifying(null);
            }}
            onCancel={() => setClassifying(null)}
          />
        )}
      </Dialog>
    </div>
  );
}

interface TotalProps {
  label: string;
  icon: "arrowDown" | "arrowUp" | "wallet" | "chart" | "split";
  tone: "in" | "out" | "neutral" | "lent";
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
  categoryLabels: Map<string, CategoryLabel>;
  showAccount: boolean;
  onClassify: (transaction: Transaction) => void;
}

function Ledger({ transactions, accountsById, categoryLabels, showAccount, onClassify }: LedgerProps) {
  const groups = groupByDate(transactions);

  return (
    <div className="ledger">
      {groups.map(({ date, rows }) => {
        const out = sumMoney(rows.filter((row) => row.amount < 0).map((row) => -row.amount));
        const moneyIn = sumMoney(rows.filter((row) => row.amount > 0).map((row) => row.amount));
        return (
          <section key={date} className="day" aria-label={formatDay(date)}>
            <header className="day__head">
              <h2 className="day__date">{formatDay(date)}</h2>
              <p className="day__total num">
                {out > 0 && <span>{formatWholeMoney(out)} out</span>}
                {moneyIn > 0 && <span className="day__in">{formatWholeMoney(moneyIn)} in</span>}
              </p>
            </header>
            <ul className="day__rows">
              {rows.map((transaction) => (
                <LedgerRow
                  key={transaction.id}
                  transaction={transaction}
                  account={showAccount ? accountsById.get(transaction.accountId) : undefined}
                  categoryLabels={categoryLabels}
                  onClassify={() => onClassify(transaction)}
                />
              ))}
            </ul>
          </section>
        );
      })}
    </div>
  );
}

function LedgerRow({
  transaction,
  account,
  categoryLabels,
  onClassify,
}: {
  transaction: Transaction;
  account: Account | undefined;
  categoryLabels: Map<string, CategoryLabel>;
  onClassify: () => void;
}) {
  const name = merchantName(transaction.description);
  const category = topCategory(transaction.allocations, categoryLabels);
  const style = category !== null ? ({ "--hue": hueOf(category) } as CSSProperties) : undefined;
  const credit = transaction.amount > 0;

  return (
    <li className={`txn${category === null ? " txn--plain" : ""}`} style={style}>
      <span className="txn__avatar" aria-hidden="true">
        {name.charAt(0)}
      </span>
      <div className="txn__main">
        <p className="txn__name" title={transaction.description}>
          {name}
        </p>
        {account && <p className="txn__meta">{account.nickname}</p>}
      </div>
      <div className="txn__tag">
        <ClassificationTag allocations={transaction.allocations} categoryLabels={categoryLabels} onClick={onClassify} />
      </div>
      <div className="txn__amounts num">
        <span className={`txn__amount${credit ? " txn__amount--in" : ""}`}>
          {credit ? formatSignedMoney(transaction.amount) : formatMoney(-transaction.amount)}
        </span>
        {transaction.balanceAfter !== null && (
          <span className="txn__balance">Bal {formatWholeMoney(transaction.balanceAfter)}</span>
        )}
      </div>
    </li>
  );
}

/** The top-level category a row is coloured by; splits take their first piece. */
function topCategory(allocations: Allocation[], categoryLabels: Map<string, CategoryLabel>): string | null {
  const [first] = allocations;
  if (first === undefined || first.categoryId === null) return null;
  const label = categoryLabels.get(first.categoryId);
  if (label === undefined) return null;
  return label.parentName ?? label.name;
}

const dayFormat = new Intl.DateTimeFormat("en-IN", { weekday: "short", day: "numeric", month: "short" });

function formatDay(isoDate: string): string {
  return dayFormat.format(new Date(`${isoDate}T00:00:00`));
}

function ClassificationTag({
  allocations,
  categoryLabels,
  onClick,
}: {
  allocations: Allocation[];
  categoryLabels: Map<string, CategoryLabel>;
  onClick: () => void;
}) {
  const [first] = allocations;
  if (first === undefined) {
    return (
      <button type="button" className="tag tag--unclassified" onClick={onClick}>
        <Icon name="plus" size={12} /> Classify
      </button>
    );
  }
  if (allocations.length > 1) {
    const kinds = [...new Set(allocations.map((allocation) => KIND_LABELS[allocation.kind]))].join(" + ");
    return (
      <button
        type="button"
        className="tag tag--split"
        title={allocations.map((a) => `${describe(a, categoryLabels)}: ${formatMoney(Math.abs(a.amount))}`).join("\n")}
        onClick={onClick}
      >
        <Icon name="split" size={12} /> Split · {kinds}
      </button>
    );
  }
  return (
    <button type="button" className={`tag tag--${first.kind.toLowerCase()}`} onClick={onClick}>
      {describe(first, categoryLabels)}
    </button>
  );
}

function describe(allocation: Allocation, categoryLabels: Map<string, CategoryLabel>): string {
  const category = allocation.categoryId !== null ? categoryLabels.get(allocation.categoryId) : undefined;
  if (category === undefined) return KIND_LABELS[allocation.kind];
  return category.parentName !== null ? `${category.parentName} › ${category.name}` : category.name;
}

function LedgerSkeleton() {
  return (
    <div className="ledger ledger--skeleton" aria-label="Loading transactions">
      {[0, 1, 2, 3, 4, 5].map((i) => (
        <div key={i} className="ledger__skeleton-row">
          <span className="skeleton skeleton--avatar" />
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
