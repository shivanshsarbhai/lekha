import { useCallback, useEffect, useState } from "react";
import { listAccounts, type Account } from "../../api/accounts";

export type AccountsState =
  | { status: "loading" }
  | { status: "success"; accounts: Account[] }
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

  const addAccount = useCallback((account: Account) => {
    setState((current) =>
      current.status === "success" ? { status: "success", accounts: [...current.accounts, account] } : current,
    );
  }, []);

  return { state, addAccount };
}
