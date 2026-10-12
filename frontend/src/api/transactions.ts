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
  /** The purchase this money came back from. Only set on positive EXPENSE pieces; they count in its month. */
  refundOf: string | null;
}

/** One element of the PUT body. Mirrors AllocationRequest.java. */
export interface AllocationInput {
  kind: AllocationKind;
  categoryId: string | null;
  /** Signed like the transaction. All pieces together must equal the transaction's amount exactly. */
  amount: number;
  note: string | null;
  /**
   * Links a refund to its purchase: kind EXPENSE, a positive amount, and one of the purchase's categories. The server
   * rejects refunds adding up to more than was spent on it.
   */
  refundOf: string | null;
}

/** Mirrors ListedTransaction.RefundedPurchase. */
export interface RefundedPurchase {
  id: string;
  transactionDate: string;
  description: string;
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
  /** How much of this purchase has come back through linked refunds. 0 for everything else. */
  refundedAmount: number;
  /** The purchases this transaction refunds, for its pieces' `refundOf` ids. */
  refundedPurchases: RefundedPurchase[];
  /** The AI's idea, only on unclassified transactions it has looked at. Never saved until you accept it. */
  suggestion: AiSuggestion | null;
}

/** Mirrors AiSuggestion.java: one piece of this kind and category for the whole amount. */
export interface AiSuggestion {
  kind: AllocationKind;
  categoryId: string | null;
  /** The model's one-line reason, e.g. "Swiggy is a food delivery app". */
  reason: string;
  model: string;
}

/** Mirrors RefundCandidate.java: a purchase a refund could be linked to. */
export interface RefundCandidate {
  id: string;
  accountId: string;
  transactionDate: string;
  description: string;
  /** Positive: what the purchase's expense pieces spent. */
  spent: number;
  /** spent minus refunds already linked to it, not counting the transaction being classified. */
  refundable: number;
  /** A refund must use one of these. Empty when the purchase's expense pieces have no category. */
  categoryIds: string[];
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

/** One transaction exactly as the list shows it, with its classification. */
export function getTransaction(id: string, signal?: AbortSignal): Promise<Transaction> {
  return apiGet<Transaction>(`/transactions/${id}`, signal);
}

/** Body of PUT /api/v1/transactions/{id}. Mirrors UpdateTransactionRequest.java; every field is required. */
export interface TransactionEdit {
  transactionDate: string;
  description: string;
  /** Signed: negative is money out. */
  amount: number;
}

/**
 * Corrects a transaction. The server keeps the bank's original date and amount in `metadata`, so re-importing the
 * statement still skips this row. Changing the amount clears the classification; the result shows it.
 */
export function updateTransaction(id: string, edit: TransactionEdit): Promise<Transaction> {
  return apiPut<TransactionEdit, Transaction>(`/transactions/${id}`, edit);
}

/**
 * Replaces how a transaction is classified. One piece covering the whole amount is a simple classification, several
 * pieces are a split, and an empty list makes it unclassified again. Resolves to the saved pieces, with new ids.
 */
export function listRefundCandidates(
  transactionId: string,
  search: string,
  signal?: AbortSignal,
): Promise<RefundCandidate[]> {
  const query = search.trim() === "" ? "" : `?${new URLSearchParams({ search: search.trim() }).toString()}`;
  return apiGet<RefundCandidate[]>(`/transactions/${transactionId}/refund-candidates${query}`, signal);
}

/** Body of PUT /api/v1/transactions/allocations. Mirrors BulkClassificationRequest.java. */
export interface BulkClassification {
  /** At most 500, each once. */
  transactionIds: string[];
  kind: AllocationKind;
  categoryId: string | null;
  note: string | null;
}

/**
 * Gives every transaction one piece of this kind and category for its whole amount, replacing what it had. All of
 * them are saved or none: an error names the transaction that broke a rule. Resolves to each one's new pieces by id.
 */
export function classifyTransactions(body: BulkClassification): Promise<Record<string, Allocation[]>> {
  return apiPut<BulkClassification, Record<string, Allocation[]>>("/transactions/allocations", body);
}

export function classifyTransaction(transactionId: string, pieces: AllocationInput[]): Promise<Allocation[]> {
  return apiPut<AllocationInput[], Allocation[]>(`/transactions/${transactionId}/allocations`, pieces);
}
