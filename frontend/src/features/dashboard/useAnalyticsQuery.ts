import { useEffect, useState } from "react";
import {
  queryAnalytics,
  type AnalyticsQuery,
  type AnalyticsResult,
  type DimensionName,
  type MetricName,
} from "../../api/analytics";

export type AnalyticsState<M extends MetricName, D extends DimensionName> =
  | { status: "loading" }
  | { status: "success"; result: AnalyticsResult<M, D> }
  | { status: "error"; message: string };

/**
 * Runs one analytics query and re-runs it whenever the query changes. Each dashboard section calls this with its own
 * question, so all of them are in flight at once and each shows its own loading or error state.
 */
export function useAnalyticsQuery<M extends MetricName, D extends DimensionName = never>(
  query: AnalyticsQuery<M, D>,
): AnalyticsState<M, D> {
  const [state, setState] = useState<AnalyticsState<M, D>>({ status: "loading" });
  // Callers build a new query object on every render; comparing it as text re-runs only when the question changes.
  const key = JSON.stringify(query);

  useEffect(() => {
    const controller = new AbortController();
    setState({ status: "loading" });

    queryAnalytics(JSON.parse(key) as AnalyticsQuery<M, D>, controller.signal)
      .then((result) => setState({ status: "success", result }))
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        setState({ status: "error", message: error instanceof Error ? error.message : "Unknown error" });
      });

    return () => controller.abort();
  }, [key]);

  return state;
}
