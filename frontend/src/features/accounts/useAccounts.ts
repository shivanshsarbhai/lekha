import { useCallback, useEffect, useState } from "react";
import { listAccounts, type Account, type ListedAccount } from "../../api/accounts";

export type AccountsState =
  | { status: "loading" }
  | { status: "success"; accounts: ListedAccount[] }
  | { status: "error"; message: string };

export function useAccounts() {
  const [state, setState] = useState<AccountsState>({ status: "loading" });

  useEffect(() => {
    const controller = new AbortController();

    listAccounts(controller.signal)
      .then((accounts) => setState({ status: "success", accounts }))
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        const message = error instanceof Error ? error.message : "Unknown error";
        setState({ status: "error", message });
      });

    return () => controller.abort();
  }, []);

  const update = useCallback((change: (accounts: ListedAccount[]) => ListedAccount[]) => {
    setState((current) => (current.status === "success" ? { status: "success", accounts: change(current.accounts) } : current));
  }, []);

  const addAccount = useCallback(
    (account: Account) => update((accounts) => [...accounts, { ...account, transactionCount: 0 }]),
    [update],
  );

  const replaceAccount = useCallback(
    (account: Account) =>
      update((accounts) => accounts.map((existing) => (existing.id === account.id ? { ...existing, ...account } : existing))),
    [update],
  );

  const removeAccount = useCallback(
    (id: string) => update((accounts) => accounts.filter((account) => account.id !== id)),
    [update],
  );

  /** Keeps the count the delete confirmation shows in step with what was just imported. */
  const countImported = useCallback(
    (id: string, imported: number) =>
      update((accounts) =>
        accounts.map((account) =>
          account.id === id ? { ...account, transactionCount: account.transactionCount + imported } : account,
        ),
      ),
    [update],
  );

  return { state, addAccount, replaceAccount, removeAccount, countImported };
}
