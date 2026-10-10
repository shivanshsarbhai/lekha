import { useCallback, useEffect, useRef, useState } from "react";
import {
  listTransactions,
  type Allocation,
  type Transaction,
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
   * After a save: shows the change on the affected rows at once, then re-fetches the list without a loading state, because
   * only the server computes the totals. An edited date may also move the row out of this month.
   */
  const applyToRows = useCallback(
    (change: (transaction: Transaction) => Transaction) => {
      setState((current) =>
        current.status !== "success"
          ? current
          : {
              status: "success",
              data: { ...current.data, transactions: current.data.transactions.map(change) },
            },
      );

      quietReload.current?.abort();
      const controller = new AbortController();
      quietReload.current = controller;
      listTransactions({ accountId, from, to }, controller.signal)
        .then((data) => setState({ status: "success", data }))
        .catch(() => {
          // The row already shows the saved change; the totals catch up on the next load.
        });
    },
    [accountId, from, to],
  );

  const applyClassification = useCallback(
    (transactionId: string, allocations: Allocation[]) =>
      applyToRows((transaction) => (transaction.id === transactionId ? { ...transaction, allocations } : transaction)),
    [applyToRows],
  );

  /** After a bulk save: `saved` has the new pieces of every transaction that changed, keyed by id. */
  const applyClassifications = useCallback(
    (saved: Record<string, Allocation[]>) =>
      applyToRows((transaction) => {
        const allocations = saved[transaction.id];
        return allocations ? { ...transaction, allocations } : transaction;
      }),
    [applyToRows],
  );

  const applyEdit = useCallback(
    (edited: Transaction) => applyToRows((transaction) => (transaction.id === edited.id ? edited : transaction)),
    [applyToRows],
  );

  return { state, applyClassification, applyClassifications, applyEdit };
}
