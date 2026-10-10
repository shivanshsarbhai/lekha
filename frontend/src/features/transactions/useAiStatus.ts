import { useCallback, useEffect, useRef, useState } from "react";
import { getAiStatus, type AiStatus } from "../../api/ai";

/** While a job runs, the status is fetched again this often. Nothing is polled while the AI is idle. */
const POLL_MS = 2500;

export function useAiStatus() {
  const [status, setStatus] = useState<AiStatus | null>(null);
  const [error, setError] = useState<string | null>(null);
  const request = useRef<AbortController | null>(null);

  const reload = useCallback(() => {
    request.current?.abort();
    const controller = new AbortController();
    request.current = controller;
    getAiStatus(controller.signal)
      .then((next) => {
        setStatus(next);
        setError(null);
      })
      .catch((cause: unknown) => {
        if (controller.signal.aborted) return;
        setError(cause instanceof Error ? cause.message : "Unknown error");
      });
  }, []);

  useEffect(() => {
    reload();
    return () => request.current?.abort();
  }, [reload]);

  const working = status?.working ?? null;
  useEffect(() => {
    if (working === null) return;
    const timer = setTimeout(reload, POLL_MS);
    return () => clearTimeout(timer);
  }, [working, status, reload]);

  /** Runs a start or stop request, which answers with the new status. */
  const run = useCallback(async (action: () => Promise<AiStatus>) => {
    try {
      setStatus(await action());
      setError(null);
    } catch (cause: unknown) {
      setError(cause instanceof Error ? cause.message : "Unknown error");
    }
  }, []);

  return { status, error, reload, run };
}
