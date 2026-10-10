import { useLayoutEffect, useRef, useState, type CSSProperties } from "react";
import { Link, useSearchParams } from "react-router";
import { AccountSelect } from "../../components/Filters";
import { Icon } from "../../components/Icon";
import { formatWholeMoney } from "../../lib/format";
import { hueOf } from "../../lib/hue";
import { currentMonth, monthRange, shiftMonth, type Month } from "../../lib/month";
import { useAccounts } from "../accounts/useAccounts";
import { Bar, byValue, compactMoney, Panel, PanelEmpty, share, type Scope } from "../dashboard/panels";
import { useAnalyticsQuery } from "../dashboard/useAnalyticsQuery";

/* ------------------------------------------------------------------------------------------------------------------
   Periods: investments are read over years, so the page picks a span of months rather than one month
   ------------------------------------------------------------------------------------------------------------------ */

type Period = "this-fy" | "last-fy" | "12m" | "5y";

const PERIODS: Period[] = ["this-fy", "last-fy", "12m", "5y"];

function isPeriod(value: string | null): value is Period {
  return PERIODS.some((period) => period === value);
}

/** Indian financial years run from April to March. */
function financialYearStart(month: Month): number {
  const year = Number(month.slice(0, 4));
  return Number(month.slice(5, 7)) >= 4 ? year : year - 1;
}

function fyLabel(startYear: number): string {
  return `FY ${startYear}–${String(startYear + 1).slice(2)}`;
}

function periodLabel(period: Period, today: Month): string {
  const fy = financialYearStart(today);
  switch (period) {
    case "this-fy":
      return `This financial year (${fyLabel(fy)})`;
    case "last-fy":
      return `Last financial year (${fyLabel(fy - 1)})`;
    case "12m":
      return "Last 12 months";
    case "5y":
      return "Last 5 years";
  }
}

function periodMonths(period: Period, today: Month): Month[] {
  const fy = financialYearStart(today);
  const [first, last] =
    period === "this-fy"
      ? [`${fy}-04`, `${fy + 1}-03`]
      : period === "last-fy"
        ? [`${fy - 1}-04`, `${fy}-03`]
        : [shiftMonth(today, period === "12m" ? -11 : -59), today];
  const months: Month[] = [];
  for (let month = first; month <= last; month = shiftMonth(month, 1)) months.push(month);
  return months;
}

const shortMonthName = new Intl.DateTimeFormat("en-IN", { month: "short" });

function shortMonth(month: Month, withYear = false): string {
  const name = shortMonthName.format(new Date(Number(month.slice(0, 4)), Number(month.slice(5, 7)) - 1, 1));
  return withYear ? `${name} ${month.slice(2, 4)}` : name;
}

/* ------------------------------------------------------------------------------------------------------------------
   Page
   ------------------------------------------------------------------------------------------------------------------ */

export function InvestmentsPage() {
  const [searchParams, setSearchParams] = useSearchParams();
  const periodParam = searchParams.get("period");
  const period: Period = isPeriod(periodParam) ? periodParam : "this-fy";
  const accountId = searchParams.get("account");
  const { state: accountsState } = useAccounts();
  const accounts = accountsState.status === "success" ? accountsState.accounts : [];
  const selectedAccount = accounts.find((account) => account.id === accountId);

  const today = currentMonth();
  const months = periodMonths(period, today);
  const firstMonth = months[0] ?? today;
  const lastMonth = months.at(-1) ?? today;
  const scope: Scope = {
    from: monthRange(firstMonth).from,
    to: monthRange(lastMonth).to,
    filters: accountId !== null ? { account: [accountId] } : {},
  };
  // Months still to come in the financial year are charted but don't count against how regular you've been.
  const elapsed = months.filter((month) => month <= today);

  const totals = useAnalyticsQuery({
    metrics: ["invested_in", "invested_out", "invested", "investment_rate", "income"],
    ...scope,
  });

  function updateFilter(changes: { period?: Period; account?: string | null }) {
    setSearchParams((current) => {
      const next = new URLSearchParams(current);
      if (changes.period !== undefined) next.set("period", changes.period);
      if (changes.account !== undefined) {
        if (changes.account === null) next.delete("account");
        else next.set("account", changes.account);
      }
      return next;
    });
  }

  const summary = totals.status === "success" ? totals.result.rows[0] : undefined;
  const unclassified = totals.status === "success" ? totals.result.unclassifiedCount : 0;
  const nothingInvested = summary !== undefined && (summary.invested_in ?? 0) === 0 && (summary.invested_out ?? 0) === 0;

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <p className="page-header__eyebrow">Investments</p>
          <h1 className="page-header__title">
            {period === "this-fy" || period === "last-fy"
              ? fyLabel(financialYearStart(firstMonth))
              : periodLabel(period, today)}
          </h1>
          <p className="page-header__subtitle">
            {shortMonth(firstMonth, true)} – {shortMonth(lastMonth, true)} ·{" "}
            {selectedAccount ? selectedAccount.nickname : "All accounts"} · money put in and taken out
          </p>
        </div>
        <div className="toolbar">
          <label className="toolbar__select">
            <span className="sr-only">Period</span>
            <select
              className="input"
              value={period}
              onChange={(event) => {
                if (isPeriod(event.target.value)) updateFilter({ period: event.target.value });
              }}
            >
              {PERIODS.map((option) => (
                <option key={option} value={option}>
                  {periodLabel(option, today)}
                </option>
              ))}
            </select>
          </label>
          <AccountSelect accounts={accounts} value={accountId} onChange={(next) => updateFilter({ account: next })} />
        </div>
      </header>

      {totals.status === "error" ? (
        <div className="callout callout--error" role="alert">
          <Icon name="alert" className="callout__icon" />
          <div>
            <p className="callout__title">Couldn't load your investments</p>
            <p className="callout__text">{totals.message}</p>
          </div>
        </div>
      ) : nothingInvested ? (
        <div className="empty">
          <span className="empty__icon">
            <Icon name="trend" size={26} />
          </span>
          <p className="empty__title">No investments in this period</p>
          <p className="empty__text">
            Classify SIPs, stock buys and FD bookings as Investment. Money coming back, like a redemption or a
            maturity, is an Investment too.
          </p>
          <Link className="button" to="/transactions">
            <Icon name="tag" /> Go to transactions
          </Link>
        </div>
      ) : (
        <>
          <Summary summary={summary} />

          {unclassified > 0 && (
            <Link className="nudge" to="/transactions?unclassified=1">
              <span className="nudge__icon">
                <Icon name="tag" size={16} />
              </span>
              <span>
                <strong>
                  {unclassified} {unclassified === 1 ? "transaction isn't" : "transactions aren't"} classified in this
                  period.
                </strong>{" "}
                Some investments may be missing until {unclassified === 1 ? "it is" : "they are"}.
              </span>
              <Icon name="chevronRight" size={16} className="nudge__arrow" />
            </Link>
          )}

          <div className="dash-grid">
            <MonthByMonth scope={scope} months={months} elapsed={elapsed} />
            <ByCategory scope={scope} />
            <Regularity scope={scope} elapsed={elapsed} />
          </div>
        </>
      )}
    </div>
  );
}

/* ------------------------------------------------------------------------------------------------------------------
   Summary tiles
   ------------------------------------------------------------------------------------------------------------------ */

interface Totals {
  invested_in: number | null;
  invested_out: number | null;
  invested: number | null;
  investment_rate: number | null;
  income: number | null;
}

function Summary({ summary }: { summary: Totals | undefined }) {
  if (summary === undefined) {
    return (
      <section className="invest-tiles" aria-label="Loading totals">
        {[0, 1, 2, 3].map((i) => (
          <div key={i} className="invest-tile">
            <span className="skeleton skeleton--line skeleton--short" />
            <span className="skeleton skeleton--line" />
          </div>
        ))}
      </section>
    );
  }

  const net = summary.invested ?? 0;
  const tiles = [
    { key: "in", label: "Put in", value: formatWholeMoney(summary.invested_in ?? 0), hint: "SIPs, stocks, FDs and more" },
    {
      key: "out",
      label: "Taken out",
      value: formatWholeMoney(summary.invested_out ?? 0),
      hint: "Redemptions and maturities",
    },
    {
      key: "net",
      label: "Net invested",
      value: net < 0 ? `−${formatWholeMoney(-net)}` : formatWholeMoney(net),
      hint: "Put in minus taken out",
    },
    {
      key: "rate",
      label: "Share of income",
      value: summary.investment_rate !== null ? `${Math.round(summary.investment_rate * 100)}%` : "—",
      hint:
        summary.investment_rate !== null
          ? `Net invested out of ${formatWholeMoney(summary.income ?? 0)} income`
          : "No income classified in this period",
    },
  ];

  return (
    <section className="invest-tiles">
      {tiles.map((tile) => (
        <div key={tile.key} className={`invest-tile invest-tile--${tile.key}`}>
          <p className="invest-tile__label">{tile.label}</p>
          <p className="invest-tile__value num">{tile.value}</p>
          <p className="invest-tile__hint">{tile.hint}</p>
        </div>
      ))}
    </section>
  );
}

/* ------------------------------------------------------------------------------------------------------------------
   Sections
   ------------------------------------------------------------------------------------------------------------------ */

function MonthByMonth({ scope, months, elapsed }: { scope: Scope; months: Month[]; elapsed: Month[] }) {
  const state = useAnalyticsQuery({ metrics: ["invested_in", "invested_out"], dimensions: ["month"], ...scope });
  const today = currentMonth();

  return (
    <Panel
      title="Month by month"
      subtitle="Money put in above the line, money taken out below it."
      size="full"
      state={state}
    >
      {({ rows }) => {
        const byMonth = new Map(rows.map((row) => [row.month, row]));
        const values = months.map((month) => ({
          month,
          in: byMonth.get(month)?.invested_in ?? 0,
          out: byMonth.get(month)?.invested_out ?? 0,
        }));
        const max = Math.max(0, ...values.map((value) => Math.max(value.in, value.out)));
        const maxOut = Math.max(0, ...values.map((value) => value.out));
        const counted = values.filter((value) => elapsed.includes(value.month));
        const monthsWithMoneyIn = counted.filter((value) => value.in > 0).length;
        const totalIn = counted.reduce((sum, value) => sum + value.in, 0);
        const everyLabel = months.length <= 12 ? 1 : months.length <= 24 ? 3 : 6;
        return (
          <>
            {counted.length > 0 && (
              <p className="panel__lead">
                Money went in during <strong>{monthsWithMoneyIn}</strong> of {counted.length}{" "}
                {counted.length === 1 ? "month" : "months"} · about{" "}
                <strong>{formatWholeMoney(totalIn / counted.length)}</strong> a month
              </p>
            )}
            <div
              className="flows"
              role="img"
              aria-label="Money put into and taken out of investments each month"
              style={{ "--out-share": max > 0 ? maxOut / max : 0 } as CSSProperties}
            >
              {values.map((value, i) => (
                <div
                  key={value.month}
                  className={`flows__month${value.month > today ? " flows__month--future" : ""}${value.in === 0 && value.month <= today ? " flows__month--gap" : ""}`}
                  title={`${shortMonth(value.month, true)}: ${formatWholeMoney(value.in)} in${value.out > 0 ? `, ${formatWholeMoney(value.out)} out` : ""}`}
                >
                  <span className="flows__in">
                    <span className="flows__bar" style={{ height: `${max > 0 ? (value.in / max) * 100 : 0}%` }} />
                  </span>
                  <span className="flows__out">
                    <span
                      className="flows__bar flows__bar--out"
                      style={{ height: `${maxOut > 0 ? (value.out / maxOut) * 100 : 0}%` }}
                    />
                  </span>
                  <span className="flows__label">
                    {i % everyLabel === 0 ? shortMonth(value.month, months.length > 12) : ""}
                  </span>
                </div>
              ))}
            </div>
          </>
        );
      }}
    </Panel>
  );
}

function ByCategory({ scope }: { scope: Scope }) {
  const state = useAnalyticsQuery({ metrics: ["invested_in", "invested_out"], dimensions: ["category"], ...scope });
  const [openId, setOpenId] = useState<string | null>(null);

  return (
    <Panel title="Where it went" subtitle="Money put in by category. Open one to see what's inside." size="full" state={state}>
      {({ rows }) => {
        const items = byValue(
          rows.filter((row) => (row.invested_in ?? 0) !== 0 || (row.invested_out ?? 0) !== 0),
          (row) => row.invested_in,
        );
        if (items.length === 0) return <PanelEmpty>Nothing invested in this period.</PanelEmpty>;
        const total = items.reduce((sum, row) => sum + (row.invested_in ?? 0), 0);
        const max = Math.max(...items.map((row) => row.invested_in ?? 0));
        return (
          <ul className="bars">
            {items.map((row) => {
              const id = row.category_id;
              const open = id !== null && id === openId;
              const out = row.invested_out ?? 0;
              const parts = [share(row.invested_in ?? 0, total), out > 0 ? `${compactMoney(out)} out` : ""];
              return (
                <li key={id ?? "uncategorised"} className={open ? "bars__open" : undefined}>
                  <Bar
                    label={row.category}
                    avatar={id !== null ? row.category : null}
                    value={row.invested_in ?? 0}
                    max={max}
                    detail={parts.filter((part) => part !== "").join(" · ")}
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
    metrics: ["invested_in"],
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

  const items = byValue(state.result.rows.filter((row) => (row.invested_in ?? 0) !== 0), (row) => row.invested_in);
  if (items.length === 0) return <p className="bars__nested panel__empty">Only money taken out here.</p>;
  const max = Math.max(0, ...items.map((row) => row.invested_in ?? 0));
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
            value={row.invested_in ?? 0}
            max={max}
            small
          />
        </li>
      ))}
    </ul>
  );
}

function Regularity({ scope, elapsed }: { scope: Scope; elapsed: Month[] }) {
  const state = useAnalyticsQuery({ metrics: ["invested_in"], dimensions: ["category", "month"], ...scope });
  const scroller = useRef<HTMLDivElement>(null);

  // Long periods scroll sideways; start at the most recent months.
  useLayoutEffect(() => {
    const element = scroller.current;
    if (element !== null) element.scrollLeft = element.scrollWidth;
  }, [state.status]);

  return (
    <Panel
      title="Regularity"
      subtitle="Money put in each month, by category. Empty cells are months nothing went in."
      size="full"
      state={state}
    >
      {({ rows }) => {
        const categories = new Map<string, { id: string | null; name: string; byMonth: Map<string, number> }>();
        for (const row of rows) {
          const key = row.category_id ?? "uncategorised";
          const entry = categories.get(key) ?? { id: row.category_id, name: row.category, byMonth: new Map() };
          entry.byMonth.set(row.month, row.invested_in ?? 0);
          categories.set(key, entry);
        }
        const lines = [...categories.values()]
          .map((category) => ({
            ...category,
            total: elapsed.reduce((sum, month) => sum + (category.byMonth.get(month) ?? 0), 0),
            filled: elapsed.filter((month) => (category.byMonth.get(month) ?? 0) > 0).length,
          }))
          .filter((category) => category.total > 0)
          .sort((a, b) => b.total - a.total);
        if (lines.length === 0 || elapsed.length === 0) {
          return <PanelEmpty>Nothing has gone in yet in this period.</PanelEmpty>;
        }
        return (
          <div className="regularity" ref={scroller}>
            <table className="regularity__table">
              <thead>
                <tr>
                  <th scope="col" className="regularity__name">
                    <span className="sr-only">Category</span>
                  </th>
                  {elapsed.map((month) => (
                    <th key={month} scope="col" className="regularity__month">
                      {shortMonth(month, elapsed.length > 12)}
                    </th>
                  ))}
                  <th scope="col" className="regularity__count">
                    Months
                  </th>
                </tr>
              </thead>
              <tbody>
                {lines.map((line) => (
                  <tr key={line.id ?? "uncategorised"} style={{ "--hue": hueOf(line.name) } as CSSProperties}>
                    <th scope="row" className="regularity__name">
                      {line.name}
                    </th>
                    {elapsed.map((month) => {
                      const value = line.byMonth.get(month) ?? 0;
                      return (
                        <td
                          key={month}
                          className={`regularity__cell${value > 0 ? "" : " regularity__cell--empty"}`}
                          title={`${line.name}, ${shortMonth(month, true)}: ${formatWholeMoney(value)}`}
                        >
                          <span className="num">{value > 0 ? compactMoney(value) : "–"}</span>
                        </td>
                      );
                    })}
                    <td className="regularity__count num">
                      {line.filled}/{elapsed.length}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        );
      }}
    </Panel>
  );
}
