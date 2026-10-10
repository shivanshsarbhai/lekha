import { apiDelete, apiGet, apiPost, apiPut } from "./client";

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

/** Mirrors ListedAccount.java: an account in the list, with how many transactions deleting it would remove. */
export interface ListedAccount extends Account {
  transactionCount: number;
}

/** Body of POST /api/v1/accounts and PUT /api/v1/accounts/{id}. Mirrors CreateAccountRequest.java. */
export interface AccountInput {
  nickname: string;
  type: AccountType;
  institution: Institution;
  last4: string | null;
}

export function listAccounts(signal?: AbortSignal): Promise<ListedAccount[]> {
  return apiGet<ListedAccount[]>("/accounts", signal);
}

export function createAccount(input: AccountInput): Promise<Account> {
  return apiPost<AccountInput, Account>("/accounts", input);
}

/**
 * Replaces the account's details. Fails with 409 if the bank or type changes on an account that already has
 * transactions, since its statements were read as that bank's format.
 */
export function updateAccount(id: string, input: AccountInput): Promise<Account> {
  return apiPut<AccountInput, Account>(`/accounts/${id}`, input);
}

/** Deletes the account and, with it, all its transactions and their classifications. There is no undo. */
export function deleteAccount(id: string): Promise<void> {
  return apiDelete(`/accounts/${id}`);
}
