import { useCallback, useEffect, useRef, useState } from "react";
import {
  listTransactions,
  type Allocation,
  type TransactionFilter,
  type TransactionList,
} from "../../api/transactions";

export type TransactionsState =
  | { status: "loading" }
  | { status: "success"; data: TransactionList }
  | { status: "error"; message: string };

export function useTransactions({ accountId, from, to }: TransactionFilter) {
  const [state, setState] = useState<TransactionsState>({ status: "loading" });
  const quietReload = useRef<AbortController | null>(null);

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

    // Runs when the filter changes: cancels the now-outdated requests so their late replies can't overwrite newer data.
    return () => {
      controller.abort();
      quietReload.current?.abort();
    };
  }, [accountId, from, to]);

  /**
   * After a classification is saved: shows the saved pieces on that row at once, then re-fetches the list without a
   * loading state, because only the server computes the totals.
   */
  const applyClassification = useCallback(
    (transactionId: string, allocations: Allocation[]) => {
      setState((current) =>
        current.status !== "success"
          ? current
          : {
              status: "success",
              data: {
                ...current.data,
                transactions: current.data.transactions.map((transaction) =>
                  transaction.id === transactionId ? { ...transaction, allocations } : transaction,
                ),
              },
            },
      );

      quietReload.current?.abort();
      const controller = new AbortController();
      quietReload.current = controller;
      listTransactions({ accountId, from, to }, controller.signal)
        .then((data) => setState({ status: "success", data }))
        .catch(() => {
          // The row already shows the saved pieces; the totals catch up on the next load.
        });
    },
    [accountId, from, to],
  );

  return { state, applyClassification };
}
