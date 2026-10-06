import { useState, type CSSProperties, type ReactNode } from "react";
import { Link, useSearchParams } from "react-router";
import type { AnalyticsResult, DimensionName, MetricName } from "../../api/analytics";
import { AccountSelect, MonthPicker } from "../../components/Filters";
import { Icon } from "../../components/Icon";
import { formatLocalDate, formatWholeMoney } from "../../lib/format";
import { hueOf } from "../../lib/hue";
import { currentMonth, formatMonth, isMonth, monthRange, type Month } from "../../lib/month";
import { useAccounts } from "../accounts/useAccounts";
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
    metrics: ["spending", "income", "invested", "lent", "savings_rate", "transaction_count", "spend_count"],
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
          <h1 className="page-header__title">{formatMonth(month)}</h1>
          <p className="page-header__subtitle">
            {selectedAccount ? selectedAccount.nickname : "All accounts"} · where your money went
          </p>
        </div>
        <div className="toolbar">
          <MonthPicker month={month} onChange={(next) => updateFilter({ month: next })} />
          <AccountSelect accounts={accounts} value={accountId} onChange={(next) => updateFilter({ account: next })} />
        </div>
      </header>

      {totals.status === "error" ? (
        <div className="callout callout--error" role="alert">
          <Icon name="alert" className="callout__icon" />
          <div>
            <p className="callout__title">Couldn't load the dashboard</p>
            <p className="callout__text">{totals.message}</p>
          </div>
        </div>
      ) : nothingClassified ? (
        <div className="empty">
          <span className="empty__icon">
            <Icon name="chart" size={26} />
          </span>
          {unclassified === 0 ? (
            <>
              <p className="empty__title">Nothing in {formatMonth(month)} yet</p>
              <p className="empty__text">Import a statement that covers this month, or pick another month.</p>
              <Link className="button" to="/accounts">
                <Icon name="upload" /> Import a statement
              </Link>
            </>
          ) : (
            <>
              <p className="empty__title">Classify {formatMonth(month)} to see your dashboard</p>
              <p className="empty__text">
                {unclassified} transactions are waiting. Tell Lekha what each one was and the charts fill in.
              </p>
              <Link className="button" to={`${transactionsLink}&unclassified=1`}>
                <Icon name="tag" /> Classify transactions
              </Link>
            </>
          )}
        </div>
      ) : (
        <>
          <Hero summary={summary} from={scope.from} to={scope.to} />

          {unclassified > 0 && (
            <Link className="nudge" to={`${transactionsLink}&unclassified=1`}>
              <span className="nudge__icon">
                <Icon name="tag" size={16} />
              </span>
              <span>
                <strong>
                  {unclassified} {unclassified === 1 ? "transaction isn't" : "transactions aren't"} counted yet.
                </strong>{" "}
                Classify {unclassified === 1 ? "it" : "them"} to complete this month.
              </span>
              <Icon name="chevronRight" size={16} className="nudge__arrow" />
            </Link>
          )}

          <div className="dash-grid">
            <SpendingByCategory scope={scope} />
            <ThroughTheMonth scope={scope} />
            <WeekdaysAndWeekends scope={scope} />
            <DaysOfTheWeek scope={scope} />
          </div>
        </>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------------------------------------------------------
   Hero: what you spent, and where your income went
   ------------------------------------------------------------------------------------------------------------------ */

interface Summary {
  spending: number | null;
  income: number | null;
  invested: number | null;
  lent: number | null;
  savings_rate: number | null;
  spend_count: number | null;
}

function Hero({ summary, from, to }: { summary: Summary | undefined; from: string; to: string }) {
  if (summary === undefined) {
    return (
      <section className="hero hero--loading" aria-label="Loading the month">
        <span className="skeleton skeleton--line skeleton--short" />
        <span className="skeleton skeleton--hero" />
        <span className="skeleton skeleton--line" />
      </section>
    );
  }

  const spent = summary.spending ?? 0;
  const income = summary.income ?? 0;
  const invested = summary.invested ?? 0;
  const lent = summary.lent ?? 0;
  const days = daysSoFar(from, to);
  const leftOver = income - spent - invested - lent;
  const whole = Math.max(income, spent + invested + Math.max(lent, 0));
  const parts = [
    { key: "spent", label: "Spent", value: spent },
    { key: "invested", label: "Invested", value: invested },
    { key: "lent", label: "Lent", value: Math.max(lent, 0) },
    { key: "left", label: leftOver >= 0 ? "Left over" : "Over income", value: Math.abs(leftOver) },
  ].filter((part) => part.value > 0);

  return (
    <section className="hero">
      <div className="hero__main">
        <p className="hero__label">Total spent</p>
        <p className="hero__value num">{formatWholeMoney(spent)}</p>
        <p className="hero__meta">
          {summary.spend_count ?? 0} spends · about {formatWholeMoney(days > 0 ? spent / days : 0)} a day
        </p>
      </div>

      <div className="hero__split">
        <div className="hero__split-head">
          <div>
            <p className="hero__label">Income</p>
            <p className="hero__income num">{formatWholeMoney(income)}</p>
          </div>
          {summary.savings_rate !== null && (
            <span className={`pill ${summary.savings_rate >= 0 ? "pill--good" : "pill--bad"}`}>
              {Math.round(summary.savings_rate * 100)}% saved
            </span>
          )}
        </div>

        {whole > 0 && (
          <>
            <div className="stack" role="img" aria-label="How this month's income was used">
              {parts.map((part) => (
                <span
                  key={part.key}
                  className={`stack__part stack__part--${part.key}${part.key === "left" && leftOver < 0 ? " stack__part--over" : ""}`}
                  style={{ flexGrow: part.value }}
                  title={`${part.label}: ${formatWholeMoney(part.value)}`}
                />
              ))}
            </div>
            <ul className="legend">
              {parts.map((part) => (
                <li key={part.key} className="legend__item">
                  <span
                    className={`legend__dot stack__part--${part.key}${part.key === "left" && leftOver < 0 ? " stack__part--over" : ""}`}
                  />
                  <span className="legend__label">{part.label}</span>
                  <span className="legend__value num">{formatWholeMoney(part.value)}</span>
                  {income > 0 && <span className="legend__share">{Math.round((part.value / income) * 100)}%</span>}
                </li>
              ))}
            </ul>
          </>
        )}
      </div>
    </section>
  );
}

/* ------------------------------------------------------------------------------------------------------------------
   Sections
   ------------------------------------------------------------------------------------------------------------------ */

function SpendingByCategory({ scope }: { scope: Scope }) {
  const state = useAnalyticsQuery({ metrics: ["spending"], dimensions: ["category"], ...scope });
  const [openId, setOpenId] = useState<string | null>(null);

  return (
    <Panel title="Where it went" subtitle="Spending by category. Open one to see what's inside." size="full" state={state}>
      {({ rows }) => {
        const items = byValue(rows.filter((row) => (row.spending ?? 0) !== 0), (row) => row.spending);
        if (items.length === 0) return <PanelEmpty>No spending this month.</PanelEmpty>;
        const total = items.reduce((sum, row) => sum + (row.spending ?? 0), 0);
        const max = Math.max(...items.map((row) => row.spending ?? 0));
        return (
          <ul className="bars">
            {items.map((row) => {
              const id = row.category_id;
              const open = id !== null && id === openId;
              return (
                <li key={id ?? "uncategorised"} className={open ? "bars__open" : undefined}>
                  <Bar
                    label={row.category}
                    avatar={id !== null ? row.category : null}
                    value={row.spending ?? 0}
                    max={max}
                    detail={share(row.spending ?? 0, total)}
                    expanded={id !== null ? open : undefined}
                    onClick={id !== null ? () => setOpenId(open ? null : id) : undefined}
                  />
                  {open && <SubcategoryBreakdown scope={scope} categoryId={id} hueName={row.category} />}
                </li>
              );
            })}
          </ul>
        );
      }}
    </Panel>
  );
}

function SubcategoryBreakdown({ scope, categoryId, hueName }: { scope: Scope; categoryId: string; hueName: string }) {
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

  const items = byValue(state.result.rows.filter((row) => (row.spending ?? 0) !== 0), (row) => row.spending);
  const max = Math.max(0, ...items.map((row) => row.spending ?? 0));
  return (
    <ul className="bars bars__nested" style={{ "--hue": hueOf(hueName) } as CSSProperties}>
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

const PHASES = [
  { key: "EARLY", label: "1st – 10th" },
  { key: "MID", label: "11th – 20th" },
  { key: "LATE", label: "21st onwards" },
] as const;

function ThroughTheMonth({ scope }: { scope: Scope }) {
  const daily = useAnalyticsQuery({ metrics: ["spending"], dimensions: ["day"], ...scope });
  const phases = useAnalyticsQuery({ metrics: ["spending"], dimensions: ["month_phase"], ...scope });

  return (
    <Panel title="Through the month" subtitle="Daily spending. Weekends are shaded." size="full" state={daily}>
      {({ rows }) => {
        const max = Math.max(0, ...rows.map((row) => row.spending ?? 0));
        const biggest = rows.reduce<(typeof rows)[number] | undefined>(
          (best, row) => ((row.spending ?? 0) > (best?.spending ?? 0) ? row : best),
          undefined,
        );
        return (
          <>
            <div className="daily" role="img" aria-label="Spending for each day of the month">
              {rows.map((row) => {
                const value = row.spending ?? 0;
                const dayNumber = Number(row.day.slice(8, 10));
                return (
                  <div
                    key={row.day}
                    className={`daily__day${isWeekend(row.day) ? " daily__day--weekend" : ""}${row === biggest ? " daily__day--peak" : ""}`}
                  >
                    <span className="daily__tip num">
                      {formatLocalDate(row.day)} · {formatWholeMoney(value)}
                    </span>
                    <span
                      className="daily__bar"
                      style={{ height: `${max > 0 ? (Math.max(value, 0) / max) * 100 : 0}%` }}
                    />
                    <span className="daily__label">{dayNumber === 1 || dayNumber % 5 === 0 ? dayNumber : ""}</span>
                  </div>
                );
              })}
            </div>
            <div className="phases">
              {biggest !== undefined && (biggest.spending ?? 0) > 0 && (
                <div className="phases__item phases__item--peak">
                  <span className="phases__label">Biggest day</span>
                  <span className="phases__value num">{formatWholeMoney(biggest.spending ?? 0)}</span>
                  <span className="phases__hint">{formatLocalDate(biggest.day)}</span>
                </div>
              )}
              {PHASES.map((phase) => {
                const row =
                  phases.status === "success"
                    ? phases.result.rows.find((candidate) => candidate.month_phase === phase.key)
                    : undefined;
                return (
                  <div key={phase.key} className="phases__item">
                    <span className="phases__label">{phase.label}</span>
                    <span className="phases__value num">
                      {phases.status === "success" ? formatWholeMoney(row?.spending ?? 0) : "…"}
                    </span>
                  </div>
                );
              })}
            </div>
          </>
        );
      }}
    </Panel>
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
    <Panel title="Weekdays vs weekends" subtitle="Saturdays and Sundays against the rest." state={state}>
      {({ rows }) => {
        const stats = (["WEEKDAY", "WEEKEND"] as const).map((type) => {
          const row = rows.find((candidate) => candidate.day_type === type);
          const spent = row?.spending ?? 0;
          const dayCount = type === "WEEKEND" ? days.weekend : days.weekday;
          return {
            type,
            label: type === "WEEKEND" ? "Weekends" : "Weekdays",
            spent,
            perDay: dayCount > 0 ? spent / dayCount : 0,
            count: row?.spend_count ?? 0,
            average: row?.average_spend ?? null,
          };
        });
        const [weekday, weekend] = stats;
        const insight =
          weekday !== undefined && weekend !== undefined && weekday.perDay > 0 && weekend.perDay > 0
            ? weekendInsight(weekend.perDay / weekday.perDay)
            : null;
        const maxPerDay = Math.max(0, ...stats.map((stat) => stat.perDay));
        return (
          <div className="versus">
            {insight !== null && <p className="versus__insight">{insight}</p>}
            {stats.map((stat) => (
              <div key={stat.type} className={`versus__row versus__row--${stat.type.toLowerCase()}`}>
                <div className="versus__head">
                  <span className="versus__label">{stat.label}</span>
                  <span className="versus__value num">{formatWholeMoney(stat.perDay)}</span>
                  <span className="versus__unit">a day</span>
                </div>
                <span className="versus__track">
                  <span
                    className="versus__fill"
                    style={{ width: `${maxPerDay > 0 ? (stat.perDay / maxPerDay) * 100 : 0}%` }}
                  />
                </span>
                <p className="versus__facts">
                  {formatWholeMoney(stat.spent)} in total · {stat.count} spends
                  {stat.average !== null ? ` · ${formatWholeMoney(stat.average)} each` : ""}
                </p>
              </div>
            ))}
          </div>
        );
      }}
    </Panel>
  );
}

const WEEKDAYS = [
  { key: "MON", label: "M", name: "Monday" },
  { key: "TUE", label: "T", name: "Tuesday" },
  { key: "WED", label: "W", name: "Wednesday" },
  { key: "THU", label: "T", name: "Thursday" },
  { key: "FRI", label: "F", name: "Friday" },
  { key: "SAT", label: "S", name: "Saturday" },
  { key: "SUN", label: "S", name: "Sunday" },
] as const;

function DaysOfTheWeek({ scope }: { scope: Scope }) {
  const state = useAnalyticsQuery({ metrics: ["spending"], dimensions: ["day_of_week"], ...scope });

  return (
    <Panel title="By day of the week" subtitle="Which days cost the most." state={state}>
      {({ rows }) => {
        const values = WEEKDAYS.map((day) => rows.find((row) => row.day_of_week === day.key)?.spending ?? 0);
        const max = Math.max(0, ...values);
        const peak = values.indexOf(max);
        const peakDay = WEEKDAYS[peak];
        return (
          <>
            {max > 0 && peakDay !== undefined && (
              <p className="panel__lead">
                <strong>{peakDay.name}s</strong> cost the most: {formatWholeMoney(max)}
              </p>
            )}
            <div className="columns" role="img" aria-label="Spending by day of the week">
              {WEEKDAYS.map((day, i) => {
                const value = values[i] ?? 0;
                return (
                  <div key={day.key} className={`columns__item${i === peak && max > 0 ? " columns__item--peak" : ""}`}>
                    <span className="columns__value num">{value > 0 ? compactMoney(value) : ""}</span>
                    <span className="columns__track">
                      <span
                        className="columns__bar"
                        style={{ height: `${max > 0 ? (Math.max(value, 0) / max) * 100 : 0}%` }}
                      />
                    </span>
                    <span className="columns__label" title={day.name}>
                      {day.label}
                    </span>
                  </div>
                );
              })}
            </div>
          </>
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
  size = "half",
  state,
  children,
}: {
  title: string;
  subtitle: string;
  size?: "full" | "half";
  state: AnalyticsState<M, D>;
  children: (result: AnalyticsResult<M, D>) => ReactNode;
}) {
  return (
    <section className={`panel panel--${size}`}>
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
  avatar = null,
  value,
  max,
  detail,
  small = false,
  expanded,
  onClick,
}: {
  label: string;
  avatar?: string | null;
  value: number;
  max: number;
  detail?: string;
  small?: boolean;
  expanded?: boolean | undefined;
  onClick?: (() => void) | undefined;
}) {
  const width = max > 0 ? (Math.max(value, 0) / max) * 100 : 0;
  const style = avatar !== null ? ({ "--hue": hueOf(avatar) } as CSSProperties) : undefined;
  const content = (
    <>
      <span className="bar__label">
        {avatar !== null ? (
          <span className="bar__avatar" aria-hidden="true">
            {avatar.charAt(0)}
          </span>
        ) : (
          !small && <span className="bar__avatar bar__avatar--none" aria-hidden="true" />
        )}
        <span className="bar__name">{label}</span>
        {onClick && (
          <Icon name="chevronRight" size={14} className={`bar__chevron${expanded ? " bar__chevron--open" : ""}`} />
        )}
      </span>
      <span className="bar__track">
        <span className="bar__fill" style={{ width: `${width}%` }} />
      </span>
      <span className="bar__value num">
        {formatWholeMoney(value)}
        {detail !== undefined && <span className="bar__detail">{detail}</span>}
      </span>
    </>
  );
  const className = `bar${small ? " bar--small" : ""}${avatar === null ? " bar--plain" : ""}`;
  return onClick ? (
    <button type="button" className={`${className} bar--button`} style={style} aria-expanded={expanded} onClick={onClick}>
      {content}
    </button>
  ) : (
    <div className={className} style={style}>
      {content}
    </div>
  );
}

function byValue<T>(rows: T[], value: (row: T) => number | null): T[] {
  return [...rows].sort((a, b) => (value(b) ?? 0) - (value(a) ?? 0));
}

function share(value: number, total: number): string {
  return total > 0 ? `${Math.round((value / total) * 100)}%` : "";
}

function weekendInsight(ratio: number): string {
  const percent = Math.round(Math.abs(ratio - 1) * 100);
  if (percent < 5) return "You spend about the same on weekends as on weekdays.";
  return ratio > 1
    ? `A weekend day costs you ${percent}% more than a weekday.`
    : `A weekend day costs you ${percent}% less than a weekday.`;
}

/** ₹1.2k, ₹38k, ₹1.4L: short enough to sit above a narrow column. */
function compactMoney(amount: number): string {
  if (amount >= 100000) return `₹${(amount / 100000).toFixed(1).replace(/\.0$/, "")}L`;
  if (amount >= 1000) return `₹${(amount / 1000).toFixed(amount >= 10000 ? 0 : 1).replace(/\.0$/, "")}k`;
  return `₹${Math.round(amount)}`;
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

/** Days of the range that have happened, so "a day" isn't diluted by the rest of the current month. */
function daysSoFar(from: string, to: string): number {
  const start = new Date(`${from}T00:00:00`);
  const end = new Date(Math.min(new Date(`${to}T00:00:00`).getTime(), new Date().setHours(0, 0, 0, 0)));
  return Math.max(0, Math.round((end.getTime() - start.getTime()) / 86_400_000) + 1);
}
