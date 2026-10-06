import { useState, type ReactNode } from "react";
import { Link, useSearchParams } from "react-router";
import type { AnalyticsResult, DimensionName, MetricName } from "../../api/analytics";
import type { PaymentMode } from "../../api/transactions";
import { Icon, type IconName } from "../../components/Icon";
import { formatWholeMoney } from "../../lib/format";
import { currentMonth, formatMonth, isMonth, monthRange, shiftMonth, type Month } from "../../lib/month";
import { useAccounts } from "../accounts/useAccounts";
import { MODE_LABELS } from "../transactions/labels";
import { useAnalyticsQuery, type AnalyticsState } from "./useAnalyticsQuery";

/** The date range and account every section on the page shares. */
interface Scope {
  from: string;
  to: string;
  filters: Partial<Record<DimensionName, string[]>>;
}

export function DashboardPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const monthParam = searchParams.get("month");
  const month: Month = isMonth(monthParam) ? monthParam : currentMonth();
  const accountId = searchParams.get("account");
  const { state: accountsState } = useAccounts();
  const accounts = accountsState.status === "success" ? accountsState.accounts : [];
  const selectedAccount = accounts.find((account) => account.id === accountId);

  const scope: Scope = { ...monthRange(month), filters: accountId !== null ? { account: [accountId] } : {} };

  const totals = useAnalyticsQuery({
    metrics: ["spending", "income", "invested", "lent", "savings_rate", "transaction_count"],
    ...scope,
  });

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

  const transactionsLink = `/transactions?month=${month}${accountId !== null ? `&account=${accountId}` : ""}`;
  const summary = totals.status === "success" ? totals.result.rows[0] : undefined;
  const unclassified = totals.status === "success" ? totals.result.unclassifiedCount : 0;
  const nothingClassified = summary !== undefined && summary.transaction_count === 0;

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <p className="page-header__eyebrow">Dashboard</p>
          <h1 className="page-header__title">Where your money went</h1>
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
          <button
            type="button"
            className="button button--ghost button--sm"
            onClick={() => updateFilter({ month: currentMonth() })}
          >
            This month
          </button>
        )}
      </div>

      {totals.status === "error" ? (
        <div className="callout callout--error" role="alert">
          <Icon name="alert" className="callout__icon" />
          <div>
            <p className="callout__title">Couldn't load the dashboard</p>
            <p className="callout__text">{totals.message}</p>
          </div>
        </div>
      ) : (
        <>
          <dl className="metrics metrics--four">
            <Tile label="Spent" icon="arrowUp" tone="out" value={summary?.spending} />
            <Tile label="Income" icon="arrowDown" tone="in" value={summary?.income} />
            <Tile label="Invested" icon="chart" tone="neutral" value={summary?.invested} />
            <Tile label="Lent" icon="split" tone="neutral" value={summary?.lent} />
            <div className="metric">
              <span className="metric__icon">
                <Icon name="wallet" />
              </span>
              <div>
                <dt className="metric__label">Saved of income</dt>
                <dd className="metric__value">
                  {summary === undefined ? (
                    <span className="skeleton skeleton--value" />
                  ) : summary.savings_rate === null ? (
                    "—"
                  ) : (
                    `${(summary.savings_rate * 100).toFixed(1)}%`
                  )}
                </dd>
              </div>
            </div>
          </dl>

          {unclassified > 0 && !nothingClassified && (
            <div className="callout callout--info">
              <Icon name="info" className="callout__icon" />
              <div>
                <p className="callout__title">
                  {unclassified} {unclassified === 1 ? "transaction isn't" : "transactions aren't"} counted yet
                </p>
                <p className="callout__text">
                  Figures only include classified transactions.{" "}
                  <Link className="link-button" to={`${transactionsLink}&unclassified=1`}>
                    Classify them
                  </Link>
                </p>
              </div>
            </div>
          )}

          {nothingClassified ? (
            <div className="empty">
              <span className="empty__icon">
                <Icon name="chart" size={26} />
              </span>
              {unclassified === 0 ? (
                <>
                  <p className="empty__title">No transactions in {formatMonth(month)}</p>
                  <p className="empty__text">Import a statement that covers this month, or pick another month.</p>
                  <Link className="button" to="/accounts">
                    <Icon name="upload" /> Import a statement
                  </Link>
                </>
              ) : (
                <>
                  <p className="empty__title">Nothing classified in {formatMonth(month)} yet</p>
                  <p className="empty__text">
                    Classify the {unclassified} transactions from this month and the dashboard fills in.
                  </p>
                  <Link className="button" to={`${transactionsLink}&unclassified=1`}>
                    <Icon name="tag" /> Classify transactions
                  </Link>
                </>
              )}
            </div>
          ) : (
            <div className="dash-grid">
              <SpendingByCategory scope={scope} />
              <WeekdaysAndWeekends scope={scope} />
              <ThroughTheMonth scope={scope} />
              <DaysOfTheWeek scope={scope} />
              <PaymentModes scope={scope} />
            </div>
          )}
        </>
      )}
    </div>
  );
}

function Tile({
  label,
  icon,
  tone,
  value,
}: {
  label: string;
  icon: IconName;
  tone: "in" | "out" | "neutral";
  value: number | null | undefined;
}) {
  return (
    <div className={`metric metric--${tone}`}>
      <span className="metric__icon">
        <Icon name={icon} />
      </span>
      <div>
        <dt className="metric__label">{label}</dt>
        <dd className="metric__value">
          {value === undefined ? <span className="skeleton skeleton--value" /> : formatWholeMoney(value ?? 0)}
        </dd>
      </div>
    </div>
  );
}

/* ------------------------------------------------------------------------------------------------------------------
   Sections
   ------------------------------------------------------------------------------------------------------------------ */

function SpendingByCategory({ scope }: { scope: Scope }) {
  const state = useAnalyticsQuery({ metrics: ["spending"], dimensions: ["category"], ...scope });
  const [openId, setOpenId] = useState<string | null>(null);

  return (
    <Panel title="Where it went" subtitle="Spending by category. Open one to see what's inside." wide state={state}>
      {({ rows }) => {
        const items = rows
          .filter((row) => (row.spending ?? 0) !== 0)
          .sort((a, b) => (b.spending ?? 0) - (a.spending ?? 0));
        if (items.length === 0) return <PanelEmpty>No spending this month.</PanelEmpty>;
        const total = items.reduce((sum, row) => sum + (row.spending ?? 0), 0);
        const max = Math.max(...items.map((row) => row.spending ?? 0));
        return (
          <ul className="bars">
            {items.map((row) => {
              const id = row.category_id;
              const open = id !== null && id === openId;
              return (
                <li key={id ?? "uncategorised"}>
                  <Bar
                    label={row.category}
                    value={row.spending ?? 0}
                    max={max}
                    detail={share(row.spending ?? 0, total)}
                    expanded={id !== null ? open : undefined}
                    onClick={id !== null ? () => setOpenId(open ? null : id) : undefined}
                  />
                  {open && <SubcategoryBreakdown scope={scope} categoryId={id} />}
                </li>
              );
            })}
          </ul>
        );
      }}
    </Panel>
  );
}

function SubcategoryBreakdown({ scope, categoryId }: { scope: Scope; categoryId: string }) {
  const state = useAnalyticsQuery({
    metrics: ["spending"],
    dimensions: ["subcategory"],
    ...scope,
    filters: { ...scope.filters, category: [categoryId] },
  });

  if (state.status === "loading") {
    return (
      <div className="bars__nested">
        <span className="skeleton skeleton--line" />
      </div>
    );
  }
  if (state.status === "error") return <p className="bars__nested panel__error">{state.message}</p>;

  const items = state.result.rows
    .filter((row) => (row.spending ?? 0) !== 0)
    .sort((a, b) => (b.spending ?? 0) - (a.spending ?? 0));
  const max = Math.max(0, ...items.map((row) => row.spending ?? 0));
  return (
    <ul className="bars bars__nested">
      {items.map((row) => (
        <li key={row.subcategory_id ?? "none"}>
          <Bar
            label={
              row.subcategory_id === categoryId
                ? "Not in a sub-category"
                : (row.subcategory.split(" › ").at(-1) ?? row.subcategory)
            }
            value={row.spending ?? 0}
            max={max}
            small
          />
        </li>
      ))}
    </ul>
  );
}

function WeekdaysAndWeekends({ scope }: { scope: Scope }) {
  const state = useAnalyticsQuery({
    metrics: ["spending", "spend_count", "average_spend"],
    dimensions: ["day_type"],
    ...scope,
  });
  const days = countDays(scope.from, scope.to);

  return (
    <Panel title="Weekdays vs weekends" subtitle="Weekends are Saturday and Sunday." state={state}>
      {({ rows }) => (
        <div className="split-stats">
          {(["WEEKDAY", "WEEKEND"] as const).map((type) => {
            const row = rows.find((candidate) => candidate.day_type === type);
            const spent = row?.spending ?? 0;
            const average = row?.average_spend ?? null;
            const dayCount = type === "WEEKEND" ? days.weekend : days.weekday;
            return (
              <div key={type} className="split-stats__item">
                <p className="split-stats__label">{type === "WEEKEND" ? "Weekends" : "Weekdays"}</p>
                <p className="split-stats__value">{formatWholeMoney(spent)}</p>
                <dl className="split-stats__facts">
                  <div>
                    <dt>Per day</dt>
                    <dd>{formatWholeMoney(dayCount > 0 ? spent / dayCount : 0)}</dd>
                  </div>
                  <div>
                    <dt>Spends</dt>
                    <dd>{row?.spend_count ?? 0}</dd>
                  </div>
                  <div>
                    <dt>Per spend</dt>
                    <dd>{average !== null ? formatWholeMoney(average) : "—"}</dd>
                  </div>
                </dl>
              </div>
            );
          })}
        </div>
      )}
    </Panel>
  );
}

const PHASES = [
  { key: "EARLY", label: "1st – 10th" },
  { key: "MID", label: "11th – 20th" },
  { key: "LATE", label: "21st onwards" },
] as const;

function ThroughTheMonth({ scope }: { scope: Scope }) {
  const daily = useAnalyticsQuery({ metrics: ["spending"], dimensions: ["day"], ...scope });
  const phases = useAnalyticsQuery({ metrics: ["spending"], dimensions: ["month_phase"], ...scope });

  return (
    <Panel title="Through the month" subtitle="Daily spending. Weekend days are shaded." wide state={daily}>
      {({ rows }) => {
        const max = Math.max(0, ...rows.map((row) => row.spending ?? 0));
        return (
          <>
            <div className="daily" role="img" aria-label="Spending for each day of the month">
              {rows.map((row) => {
                const value = row.spending ?? 0;
                const dayNumber = Number(row.day.slice(8, 10));
                return (
                  <div
                    key={row.day}
                    className={`daily__day${isWeekend(row.day) ? " daily__day--weekend" : ""}`}
                    title={`${row.day}: ${formatWholeMoney(value)}`}
                  >
                    <span className="daily__bar" style={{ height: `${max > 0 ? (Math.max(value, 0) / max) * 100 : 0}%` }} />
                    <span className="daily__label">{dayNumber === 1 || dayNumber % 5 === 0 ? dayNumber : ""}</span>
                  </div>
                );
              })}
            </div>
            {phases.status === "success" && (
              <div className="phases">
                {PHASES.map((phase) => {
                  const row = phases.result.rows.find((candidate) => candidate.month_phase === phase.key);
                  return (
                    <div key={phase.key} className="phases__item">
                      <span className="phases__label">{phase.label}</span>
                      <span className="phases__value">{formatWholeMoney(row?.spending ?? 0)}</span>
                    </div>
                  );
                })}
              </div>
            )}
          </>
        );
      }}
    </Panel>
  );
}

const WEEKDAYS = [
  { key: "MON", label: "Mon" },
  { key: "TUE", label: "Tue" },
  { key: "WED", label: "Wed" },
  { key: "THU", label: "Thu" },
  { key: "FRI", label: "Fri" },
  { key: "SAT", label: "Sat" },
  { key: "SUN", label: "Sun" },
] as const;

function DaysOfTheWeek({ scope }: { scope: Scope }) {
  const state = useAnalyticsQuery({ metrics: ["spending"], dimensions: ["day_of_week"], ...scope });

  return (
    <Panel title="By day of the week" subtitle="Which days cost the most." state={state}>
      {({ rows }) => {
        const values = WEEKDAYS.map((day) => rows.find((row) => row.day_of_week === day.key)?.spending ?? 0);
        const max = Math.max(0, ...values);
        return (
          <ul className="bars">
            {WEEKDAYS.map((day, i) => (
              <li key={day.key}>
                <Bar label={day.label} value={values[i] ?? 0} max={max} small />
              </li>
            ))}
          </ul>
        );
      }}
    </Panel>
  );
}

function PaymentModes({ scope }: { scope: Scope }) {
  const state = useAnalyticsQuery({ metrics: ["spending"], dimensions: ["payment_mode"], ...scope });

  return (
    <Panel title="How you paid" subtitle="Spending by payment method." state={state}>
      {({ rows }) => {
        const items = rows
          .filter((row) => (row.spending ?? 0) !== 0)
          .sort((a, b) => (b.spending ?? 0) - (a.spending ?? 0));
        if (items.length === 0) return <PanelEmpty>No spending this month.</PanelEmpty>;
        const total = items.reduce((sum, row) => sum + (row.spending ?? 0), 0);
        const max = Math.max(...items.map((row) => row.spending ?? 0));
        return (
          <ul className="bars">
            {items.map((row) => (
              <li key={row.payment_mode}>
                <Bar
                  label={isPaymentMode(row.payment_mode) ? MODE_LABELS[row.payment_mode] : "Unknown"}
                  value={row.spending ?? 0}
                  max={max}
                  detail={share(row.spending ?? 0, total)}
                  small
                />
              </li>
            ))}
          </ul>
        );
      }}
    </Panel>
  );
}

/* ------------------------------------------------------------------------------------------------------------------
   Building blocks
   ------------------------------------------------------------------------------------------------------------------ */

function Panel<M extends MetricName, D extends DimensionName>({
  title,
  subtitle,
  wide = false,
  state,
  children,
}: {
  title: string;
  subtitle: string;
  wide?: boolean;
  state: AnalyticsState<M, D>;
  children: (result: AnalyticsResult<M, D>) => ReactNode;
}) {
  return (
    <section className={`panel${wide ? " panel--wide" : ""}`}>
      <header className="panel__header">
        <h2 className="panel__title">{title}</h2>
        <p className="panel__subtitle">{subtitle}</p>
      </header>
      {state.status === "loading" && (
        <div className="panel__loading" aria-label={`Loading ${title.toLowerCase()}`}>
          <span className="skeleton skeleton--line" />
          <span className="skeleton skeleton--line skeleton--short" />
          <span className="skeleton skeleton--line" />
        </div>
      )}
      {state.status === "error" && <p className="panel__error">{state.message}</p>}
      {state.status === "success" && children(state.result)}
    </section>
  );
}

function PanelEmpty({ children }: { children: ReactNode }) {
  return <p className="panel__empty">{children}</p>;
}

function Bar({
  label,
  value,
  max,
  detail,
  small = false,
  expanded,
  onClick,
}: {
  label: string;
  value: number;
  max: number;
  detail?: string;
  small?: boolean;
  expanded?: boolean | undefined;
  onClick?: (() => void) | undefined;
}) {
  const width = max > 0 ? (Math.max(value, 0) / max) * 100 : 0;
  const content = (
    <>
      <span className="bar__label">
        {onClick && <Icon name="chevronRight" size={14} className={`bar__chevron${expanded ? " bar__chevron--open" : ""}`} />}
        {label}
      </span>
      <span className="bar__track">
        <span className="bar__fill" style={{ width: `${width}%` }} />
      </span>
      <span className="bar__value">
        {formatWholeMoney(value)}
        {detail !== undefined && <span className="bar__detail">{detail}</span>}
      </span>
    </>
  );
  const className = `bar${small ? " bar--small" : ""}`;
  return onClick ? (
    <button type="button" className={`${className} bar--button`} aria-expanded={expanded} onClick={onClick}>
      {content}
    </button>
  ) : (
    <div className={className}>{content}</div>
  );
}

/** The server labels rows without a payment mode "UNKNOWN", which isn't one of the bank's modes. */
function isPaymentMode(value: string): value is PaymentMode {
  return Object.hasOwn(MODE_LABELS, value);
}

function share(value: number, total: number): string {
  return total > 0 ? `${Math.round((value / total) * 100)}%` : "";
}

/** For "YYYY-MM-DD" strings, read as local dates so the weekday never shifts across time zones. */
function isWeekend(isoDate: string): boolean {
  const day = new Date(`${isoDate}T00:00:00`).getDay();
  return day === 0 || day === 6;
}

function countDays(from: string, to: string): { weekday: number; weekend: number } {
  const counts = { weekday: 0, weekend: 0 };
  const end = new Date(`${to}T00:00:00`);
  for (const day = new Date(`${from}T00:00:00`); day <= end; day.setDate(day.getDate() + 1)) {
    if (day.getDay() === 0 || day.getDay() === 6) counts.weekend += 1;
    else counts.weekday += 1;
  }
  return counts;
}
