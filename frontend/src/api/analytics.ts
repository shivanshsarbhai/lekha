import { apiPost } from "./client";
import type { AllocationKind } from "./transactions";

export type MetricName =
  | "spending"
  | "income"
  | "invested"
  | "invested_in"
  | "invested_out"
  | "investment_rate"
  | "lent"
  | "savings_rate"
  | "transaction_count"
  | "spend_count"
  | "average_spend";

export type DimensionName =
  | "day"
  | "week"
  | "month"
  | "quarter"
  | "year"
  | "day_of_week"
  | "day_type"
  | "month_of_year"
  | "month_phase"
  | "kind"
  | "category"
  | "subcategory"
  | "account"
  | "payment_mode";

/** Dimensions the server returns with an id column (e.g. `category_id`) next to the label. */
type IdDimension = Extract<DimensionName, "category" | "subcategory" | "account">;

export interface AnalyticsQuery<M extends MetricName, D extends DimensionName> {
  metrics: M[];
  dimensions?: D[];
  /** Ids for category, subcategory and account; labels such as "WEEKEND" for the rest. */
  filters?: Partial<Record<DimensionName, string[]>>;
  from: string;
  to: string;
}

/**
 * One result row, typed from the query that produced it: asking for `["spending"]` by `["category"]` gives
 * `{ spending, category, category_id }`. Metrics are null when undefined, e.g. a savings rate with no income.
 */
export type AnalyticsRow<M extends MetricName, D extends DimensionName> = { [K in M]: number | null } & {
  [K in D]: string;
} & { [K in Extract<D, IdDimension> as `${K}_id`]: string | null };

export interface AnalyticsResult<M extends MetricName, D extends DimensionName> {
  rows: AnalyticsRow<M, D>[];
  /** Transactions in the range that nobody has classified yet, so none of the figures include them. */
  unclassifiedCount: number;
}

/** What the server sends: a table of column names and positional rows. */
interface QueryResponse {
  columns: string[];
  rows: (string | number | null)[][];
  unclassifiedCount: number;
}

/** Metrics that are a sum of money, so the pieces behind them can be listed. Rates and counts can't. */
export type DrillableMetric = Extract<MetricName, "spending" | "income" | "invested" | "invested_in" | "invested_out" | "lent">;

/** Body of POST /api/v1/analytics/pieces. Mirrors PiecesRequest.java. */
export interface PiecesQuery {
  metric: DrillableMetric;
  /** As in a query; `category: ["none"]` is what has no category. */
  filters?: Partial<Record<DimensionName, string[]>>;
  from: string;
  to: string;
}

/** One allocation piece behind a figure. Mirrors PieceList.Piece. */
export interface Piece {
  id: string;
  transactionId: string;
  transactionDate: string;
  /** The purchase's date for a refund linked to one, which is when the figure counts it. */
  reportedOn: string;
  description: string;
  transactionAmount: number;
  paymentMode: string | null;
  accountId: string;
  account: string;
  kind: AllocationKind;
  categoryId: string | null;
  /** "Food › Delivery" for a sub-category. */
  category: string | null;
  note: string | null;
  refundOf: string | null;
  /** What it adds to the figure; negative for a refund in spending. */
  contribution: number;
  /** The transaction has other pieces too. */
  split: boolean;
}

export interface PieceList {
  /** The same number the dashboard shows. */
  total: number;
  count: number;
  /** Only the biggest pieces were sent. */
  truncated: boolean;
  pieces: Piece[];
}

export function listPieces(query: PiecesQuery, signal?: AbortSignal): Promise<PieceList> {
  return apiPost<PiecesQuery, PieceList>("/analytics/pieces", query, signal);
}

export async function queryAnalytics<M extends MetricName, D extends DimensionName = never>(
  query: AnalyticsQuery<M, D>,
  signal?: AbortSignal,
): Promise<AnalyticsResult<M, D>> {
  const response = await apiPost<AnalyticsQuery<M, D>, QueryResponse>("/analytics/query", query, signal);
  return {
    rows: response.rows.map(
      (values) => Object.fromEntries(response.columns.map((column, i) => [column, values[i] ?? null])) as AnalyticsRow<M, D>,
    ),
    unclassifiedCount: response.unclassifiedCount,
  };
}
