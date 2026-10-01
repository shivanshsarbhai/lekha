import { apiGet } from "./client";

export type PaymentMode = "UPI" | "CARD" | "NEFT" | "IMPS" | "RTGS" | "ATM" | "CHEQUE" | "OTHER";

/** Mirrors Transaction.java. */
export interface Transaction {
  id: string;
  accountId: string;
  /** ISO date without time, e.g. "2026-09-03" (LocalDate). */
  transactionDate: string;
  settlementDate: string | null;
  description: string;
  /** Negative: money left the account. Positive: money came in. */
  amount: number;
  balanceAfter: number | null;
  paymentMode: PaymentMode | null;
  metadata: Record<string, string>;
  createdAt: string;
}

/** Mirrors TransactionList.java. Totals are summed exactly on the server; moneyOut is negative. */
export interface TransactionList {
  transactions: Transaction[];
  moneyIn: number;
  moneyOut: number;
  net: number;
}

export interface TransactionFilter {
  /** null means all accounts. */
  accountId: string | null;
  /** Inclusive ISO dates, at most a year apart. */
  from: string;
  to: string;
}

export function listTransactions(filter: TransactionFilter, signal?: AbortSignal): Promise<TransactionList> {
  const params = new URLSearchParams({ from: filter.from, to: filter.to });
  if (filter.accountId !== null) {
    params.set("accountId", filter.accountId);
  }
  return apiGet<TransactionList>(`/transactions?${params.toString()}`, signal);
}
