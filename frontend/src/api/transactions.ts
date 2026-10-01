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
