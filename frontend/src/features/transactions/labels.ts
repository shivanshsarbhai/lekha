import type { PaymentMode } from "../../api/transactions";

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
