import { apiGet, apiPost } from "./client";

export type AccountType = "BANK" | "CREDIT_CARD";

export type Institution = "HDFC" | "SBI" | "FEDERAL_BANK";

export interface Account {
  id: string;
  nickname: string;
  type: AccountType;
  institution: Institution;
  last4: string | null;
  /** ISO-8601 instant; JSON has no Date type, so parse it where it is displayed. */
  createdAt: string;
}

/** Body of POST /api/v1/accounts. Mirrors CreateAccountRequest.java. */
export interface CreateAccountInput {
  nickname: string;
  type: AccountType;
  institution: Institution;
  last4: string | null;
}

export function listAccounts(signal?: AbortSignal): Promise<Account[]> {
  return apiGet<Account[]>("/accounts", signal);
}

export function createAccount(input: CreateAccountInput): Promise<Account> {
  return apiPost<CreateAccountInput, Account>("/accounts", input);
}
