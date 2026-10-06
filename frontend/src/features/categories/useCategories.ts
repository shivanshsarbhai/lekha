import { useEffect, useMemo, useState } from "react";
import { listCategories, type CategoryNode } from "../../api/categories";

export type CategoriesState =
  | { status: "loading" }
  | { status: "success"; tree: CategoryNode[] }
  | { status: "error"; message: string };

export interface CategoryLabel {
  name: string;
  /** The top-level category's name, for sub-categories. */
  parentName: string | null;
}

/** The category tree, plus a lookup from id to name for showing allocations, which carry only ids. */
export function useCategories() {
  const [state, setState] = useState<CategoriesState>({ status: "loading" });

  useEffect(() => {
    const controller = new AbortController();

    listCategories(controller.signal)
      .then((tree) => setState({ status: "success", tree }))
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        const message = error instanceof Error ? error.message : "Unknown error";
        setState({ status: "error", message });
      });

    return () => controller.abort();
  }, []);

  const labels = useMemo(() => {
    const byId = new Map<string, CategoryLabel>();
    if (state.status !== "success") return byId;
    for (const parent of state.tree) {
      byId.set(parent.id, { name: parent.name, parentName: null });
      for (const child of parent.children) {
        byId.set(child.id, { name: child.name, parentName: parent.name });
      }
    }
    return byId;
  }, [state]);

  return { state, labels };
}
