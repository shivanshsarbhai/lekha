import { apiPostForm } from "./client";
import type { Transaction } from "./transactions";

/** Mirrors StatementImport.java. */
export interface StatementImport {
  imported: Transaction[];
  skipped: number;
}

export function uploadStatement(accountId: string, file: File): Promise<StatementImport> {
  const form = new FormData();
  // The part name must match `@RequestParam MultipartFile file` in StatementController.
  form.append("file", file);
  return apiPostForm<StatementImport>(`/accounts/${accountId}/statements`, form);
}
