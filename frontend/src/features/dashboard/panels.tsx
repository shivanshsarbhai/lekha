import type { CSSProperties, ReactNode } from "react";
import type { AnalyticsResult, DimensionName, MetricName } from "../../api/analytics";
import { Icon } from "../../components/Icon";
import { formatWholeMoney } from "../../lib/format";
import { hueOf } from "../../lib/hue";
import type { AnalyticsState } from "./useAnalyticsQuery";

/** The date range and filters every section on a page shares. */
export interface Scope {
  from: string;
  to: string;
  filters: Partial<Record<DimensionName, string[]>>;
}

export function Panel<M extends MetricName, D extends DimensionName>({
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

export function PanelEmpty({ children }: { children: ReactNode }) {
  return <p className="panel__empty">{children}</p>;
}

export function Bar({
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

export function byValue<T>(rows: T[], value: (row: T) => number | null): T[] {
  return [...rows].sort((a, b) => (value(b) ?? 0) - (value(a) ?? 0));
}

export function share(value: number, total: number): string {
  return total > 0 ? `${Math.round((value / total) * 100)}%` : "";
}

/** ₹1.2k, ₹38k, ₹1.4L: short enough to sit above a narrow column. */
export function compactMoney(amount: number): string {
  if (amount >= 100000) return `₹${(amount / 100000).toFixed(1).replace(/\.0$/, "")}L`;
  if (amount >= 1000) return `₹${(amount / 1000).toFixed(amount >= 10000 ? 0 : 1).replace(/\.0$/, "")}k`;
  return `₹${Math.round(amount)}`;
}
