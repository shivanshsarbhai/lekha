import { useEffect, useState } from "react";
import { listTransactions, type TransactionFilter, type TransactionList } from "../../api/transactions";

export type TransactionsState =
  | { status: "loading" }
  | { status: "success"; data: TransactionList }
  | { status: "error"; message: string };

export function useTransactions({ accountId, from, to }: TransactionFilter): TransactionsState {
  const [state, setState] = useState<TransactionsState>({ status: "loading" });

  useEffect(() => {
    const controller = new AbortController();
    setState({ status: "loading" });

    listTransactions({ accountId, from, to }, controller.signal)
      .then((data) => setState({ status: "success", data }))
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        const message = error instanceof Error ? error.message : "Unknown error";
        setState({ status: "error", message });
      });

    // Runs when the filter changes: cancels the now-outdated request so its late reply can't overwrite newer data.
    return () => controller.abort();
  }, [accountId, from, to]);

  return state;
}
