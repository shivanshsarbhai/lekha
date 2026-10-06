import { apiDelete, apiGet, apiPatch, apiPost } from "./client";

/** Mirrors CategoryKind.java. */
export type CategoryKind = "EXPENSE" | "INCOME" | "INVESTMENT" | "TRANSFER";

/** Mirrors CategoryNode.java. Two levels: top-level categories, each with its sub-categories. */
export interface CategoryNode {
  id: string;
  name: string;
  kind: CategoryKind;
  children: CategoryNode[];
}

/** Mirrors Category.java, as returned by create and rename. */
export interface Category {
  id: string;
  parentId: string | null;
  name: string;
  kind: CategoryKind;
  createdAt: string;
}

/**
 * Body of POST /api/v1/categories. Mirrors CreateCategoryRequest.java. A top-level category needs a kind; a
 * sub-category takes its parent's, so send `kind: null` with a `parentId`.
 */
export interface CreateCategoryInput {
  name: string;
  kind: CategoryKind | null;
  parentId: string | null;
}

export function listCategories(signal?: AbortSignal): Promise<CategoryNode[]> {
  return apiGet<CategoryNode[]>("/categories", signal);
}

/** Fails with 409 when the name is already taken at that level (ignoring case). */
export function createCategory(input: CreateCategoryInput): Promise<Category> {
  return apiPost<CreateCategoryInput, Category>("/categories", input);
}

export function renameCategory(id: string, name: string): Promise<Category> {
  return apiPatch<{ name: string }, Category>(`/categories/${id}`, { name });
}

/** Fails with 409 while the category has sub-categories or is used by a classified transaction. */
export function deleteCategory(id: string): Promise<void> {
  return apiDelete(`/categories/${id}`);
}
