import type { AccountType, Institution } from "../../api/accounts";

export const TYPE_LABELS: Record<AccountType, string> = {
  BANK: "Bank account",
  CREDIT_CARD: "Credit card",
};

export const INSTITUTION_LABELS: Record<Institution, string> = {
  HDFC: "HDFC Bank",
  SBI: "State Bank of India",
  FEDERAL_BANK: "Federal Bank",
};

export const INSTITUTION_SHORT: Record<Institution, string> = {
  HDFC: "HD",
  SBI: "SB",
  FEDERAL_BANK: "FB",
};

export const INSTITUTIONS = Object.keys(INSTITUTION_LABELS) as Institution[];
