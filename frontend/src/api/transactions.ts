import { apiGet, apiPut } from "./client";

export type PaymentMode = "UPI" | "CARD" | "NEFT" | "IMPS" | "RTGS" | "ATM" | "CHEQUE" | "OTHER";

/** Mirrors AllocationKind.java. */
export type AllocationKind = "EXPENSE" | "INCOME" | "TRANSFER" | "INVESTMENT" | "LENT";

/** Mirrors Allocation.java. One piece of a transaction; a transaction's pieces add up exactly to its amount. */
export interface Allocation {
  /** Regenerated on every save, so never keep one across saves. */
  id: string;
  transactionId: string;
  kind: AllocationKind;
  /** Look the name up in the category tree. Always null for LENT. */
  categoryId: string | null;
  /** Same sign rule as the transaction: negative is money out. */
  amount: number;
  note: string | null;
}

/** One element of the PUT body. Mirrors AllocationRequest.java. */
export interface AllocationInput {
  kind: AllocationKind;
  categoryId: string | null;
  /** Signed like the transaction. All pieces together must equal the transaction's amount exactly. */
  amount: number;
  note: string | null;
}

/** Mirrors ListedTransaction.java: the bank's fields at the top level, plus how it has been classified. */
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
  /** Empty means unclassified. Sorted by kind, then larger amount first. */
  allocations: Allocation[];
}

/** Mirrors TransactionList.java. Every total is summed exactly on the server. */
export interface TransactionList {
  transactions: Transaction[];
  /** Cash flow, as the bank sees it: moneyOut is negative and transfers count. */
  moneyIn: number;
  moneyOut: number;
  net: number;
  /** Classified totals, positive in the usual case: expenses net of refunds, and so on. Transfers count in none. */
  spending: number;
  income: number;
  invested: number;
  lent: number;
  unclassifiedCount: number;
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

/**
 * Replaces how a transaction is classified. One piece covering the whole amount is a simple classification, several
 * pieces are a split, and an empty list makes it unclassified again. Resolves to the saved pieces, with new ids.
 */
export function classifyTransaction(transactionId: string, pieces: AllocationInput[]): Promise<Allocation[]> {
  return apiPut<AllocationInput[], Allocation[]>(`/transactions/${transactionId}/allocations`, pieces);
}
