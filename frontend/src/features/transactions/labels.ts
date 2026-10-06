import type { AllocationKind, PaymentMode } from "../../api/transactions";

export const MODE_LABELS: Record<PaymentMode, string> = {
  UPI: "UPI",
  CARD: "Card",
  NEFT: "NEFT",
  IMPS: "IMPS",
  RTGS: "RTGS",
  ATM: "ATM",
  CHEQUE: "Cheque",
  OTHER: "Other",
};

export const KIND_LABELS: Record<AllocationKind, string> = {
  EXPENSE: "Expense",
  INCOME: "Income",
  TRANSFER: "Transfer",
  INVESTMENT: "Investment",
  LENT: "Lent",
};
