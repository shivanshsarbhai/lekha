import { apiPostForm } from "./client";
import type { Transaction } from "./transactions";

/** Mirrors StatementImport.java. */
export interface StatementImport {
  imported: Transaction[];
  skipped: number;
}

/** `code` on the 422 ApiError when the PDF is locked: ask for the password, then upload again with it. */
export type StatementPasswordCode = "PASSWORD_REQUIRED" | "WRONG_PASSWORD";

/**
 * `password` unlocks a password-protected PDF. It goes in the multipart body rather than the URL, so it stays out of
 * server and proxy logs; the server uses it only in memory.
 */
export function uploadStatement(accountId: string, file: File, password?: string): Promise<StatementImport> {
  const form = new FormData();
  // The part names must match the @RequestParam names in StatementController.
  form.append("file", file);
  if (password !== undefined && password !== "") form.append("password", password);
  return apiPostForm<StatementImport>(`/accounts/${accountId}/statements`, form);
}
