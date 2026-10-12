import { createContext, useContext, type KeyboardEvent } from "react";
import type { PiecesQuery } from "../../api/analytics";
import { formatLocalDate } from "../../lib/format";

/** A figure to open up: what the list is titled, and the pieces query that adds up to it. */
export interface DrillTarget {
  title: string;
  subtitle?: string;
  query: PiecesQuery;
}

interface DrillDown {
  open: (target: DrillTarget) => void;
  /** Goes up whenever a transaction is re-classified, so every figure on the page fetches itself again. */
  version: number;
}

export const DrillDownContext = createContext<DrillDown>({ open: () => undefined, version: 0 });

export function useDrillDown(): (target: DrillTarget) => void {
  return useContext(DrillDownContext).open;
}

export function useDataVersion(): number {
  return useContext(DrillDownContext).version;
}

export function rangeLabel(from: string, to: string): string {
  return from === to ? formatLocalDate(from) : `${formatLocalDate(from)} – ${formatLocalDate(to)}`;
}

/**
 * Makes a tile or chart cell open a drill-down. These hold block content, which a real <button> can't, so they get
 * the button role and keyboard handling instead.
 */
export function drillable(onOpen: () => void, label: string) {
  return {
    role: "button",
    tabIndex: 0,
    "aria-label": label,
    onClick: onOpen,
    onKeyDown: (event: KeyboardEvent) => {
      if (event.key === "Enter" || event.key === " ") {
        event.preventDefault();
        onOpen();
      }
    },
  } as const;
}
