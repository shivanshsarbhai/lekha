import { apiGet } from "./client";

/** Mirrors CategoryKind.java. */
export type CategoryKind = "EXPENSE" | "INCOME" | "INVESTMENT";

/** Mirrors CategoryNode.java. Two levels: top-level categories, each with its sub-categories. */
export interface CategoryNode {
  id: string;
  name: string;
  kind: CategoryKind;
  children: CategoryNode[];
}

export function listCategories(signal?: AbortSignal): Promise<CategoryNode[]> {
  return apiGet<CategoryNode[]>("/categories", signal);
}
