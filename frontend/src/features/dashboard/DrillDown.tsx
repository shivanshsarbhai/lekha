import { useEffect, useMemo, useState, type CSSProperties, type ReactNode } from "react";
import { listPieces, type Piece, type PieceList, type PiecesQuery } from "../../api/analytics";
import { getTransaction, type Transaction } from "../../api/transactions";
import { Dialog } from "../../components/Dialog";
import { Icon } from "../../components/Icon";
import { formatLocalDate, formatMoney, formatWholeMoney } from "../../lib/format";
import { hueOf } from "../../lib/hue";
import { useCategories } from "../categories/useCategories";
import { ClassifyTransaction } from "../transactions/ClassifyTransaction";
import { KIND_LABELS } from "../transactions/labels";
import { DrillDownContext, type DrillTarget } from "./drill";

type Editing =
  | { status: "loading" }
  | { status: "ready"; transaction: Transaction }
  | { status: "error"; message: string };

/**
 * Lets anything on the page open the transactions behind a figure. Picking one re-classifies it right there; saving
 * bumps the data version, so the list and every figure on the page fetch themselves again.
 */
export function DrillDownProvider({ children }: { children: ReactNode }) {
  const [target, setTarget] = useState<DrillTarget | null>(null);
  const [editing, setEditing] = useState<Editing | null>(null);
  const [version, setVersion] = useState(0);
  const { state: categoriesState } = useCategories();
  const categories = categoriesState.status === "success" ? categoriesState.tree : [];

  const context = useMemo(() => ({ open: setTarget, version }), [version]);

  function close() {
    setTarget(null);
    setEditing(null);
  }

  function edit(piece: Piece) {
    setEditing({ status: "loading" });
    getTransaction(piece.transactionId)
      .then((transaction) => setEditing({ status: "ready", transaction }))
      .catch((error: unknown) =>
        setEditing({ status: "error", message: error instanceof Error ? error.message : "Unknown error" }),
      );
  }

  const title = editing !== null ? "Classify transaction" : (target?.title ?? "");
  const description =
    editing?.status === "ready" ? editing.transaction.description : editing === null ? target?.subtitle : undefined;

  return (
    <DrillDownContext.Provider value={context}>
      {children}
      <Dialog open={target !== null} onClose={close} title={title} size="lg" {...(description ? { description } : {})}>
        {target !== null &&
          (editing === null ? (
            <Pieces query={target.query} version={version} onPick={edit} />
          ) : editing.status === "loading" ? (
            <p className="pieces__status">
              <span className="spinner" aria-hidden="true" /> Opening the transaction…
            </p>
          ) : editing.status === "error" ? (
            <>
              <p className="panel__error">{editing.message}</p>
              <div className="dialog__actions">
                <button type="button" className="button button--secondary" onClick={() => setEditing(null)}>
                  Back to the list
                </button>
              </div>
            </>
          ) : (
            <ClassifyTransaction
              transaction={editing.transaction}
              categories={categories}
              onSaved={() => {
                setEditing(null);
                setVersion((current) => current + 1);
              }}
              onCancel={() => setEditing(null)}
            />
          ))}
      </Dialog>
    </DrillDownContext.Provider>
  );
}

type PiecesState = { status: "loading" } | { status: "success"; list: PieceList } | { status: "error"; message: string };

function Pieces({ query, version, onPick }: { query: PiecesQuery; version: number; onPick: (piece: Piece) => void }) {
  const [state, setState] = useState<PiecesState>({ status: "loading" });
  const key = JSON.stringify(query);

  useEffect(() => {
    const controller = new AbortController();
    listPieces(JSON.parse(key) as PiecesQuery, controller.signal)
      .then((list) => setState({ status: "success", list }))
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        setState({ status: "error", message: error instanceof Error ? error.message : "Unknown error" });
      });
    return () => controller.abort();
  }, [key, version]);

  if (state.status === "loading") {
    return (
      <div className="pieces__loading" aria-label="Loading the transactions">
        <span className="skeleton skeleton--line" />
        <span className="skeleton skeleton--line skeleton--short" />
        <span className="skeleton skeleton--line" />
      </div>
    );
  }
  if (state.status === "error") return <p className="panel__error">{state.message}</p>;

  const { list } = state;
  if (list.count === 0) return <p className="panel__empty">Nothing here.</p>;
  return (
    <>
      <p className="pieces__summary">
        <strong className="num">{formatWholeMoney(list.total)}</strong> from {list.count}{" "}
        {list.count === 1 ? "transaction" : "transactions"}
        {list.truncated ? `. Showing the biggest ${list.pieces.length}.` : "."} Pick one to re-classify it.
      </p>
      <ul className="pieces">
        {list.pieces.map((piece) => (
          <li key={piece.id}>
            <PieceRow piece={piece} onPick={() => onPick(piece)} />
          </li>
        ))}
      </ul>
    </>
  );
}

function PieceRow({ piece, onPick }: { piece: Piece; onPick: () => void }) {
  const topCategory = piece.category?.split(" › ")[0] ?? null;
  const facts = [
    piece.account,
    piece.category ?? KIND_LABELS[piece.kind],
    piece.split ? `part of ${formatMoney(Math.abs(piece.transactionAmount))}` : null,
    piece.note,
  ].filter((fact): fact is string => fact !== null && fact !== "");
  const isRefund = piece.refundOf !== null;
  return (
    <button
      type="button"
      className={`piece${topCategory === null ? " piece--plain" : ""}`}
      style={topCategory !== null ? ({ "--hue": hueOf(topCategory) } as CSSProperties) : undefined}
      onClick={onPick}
    >
      <span className="piece__avatar" aria-hidden="true">
        {topCategory?.charAt(0) ?? ""}
      </span>
      <span className="piece__main">
        <span className="piece__name">{piece.description}</span>
        <span className="piece__meta">
          {formatLocalDate(piece.transactionDate)} · {facts.join(" · ")}
        </span>
        {isRefund && piece.reportedOn !== piece.transactionDate && (
          <span className="piece__refund">Refund, counted with its purchase on {formatLocalDate(piece.reportedOn)}</span>
        )}
      </span>
      <span className={`piece__amount num${piece.contribution < 0 ? " piece__amount--back" : ""}`}>
        {piece.contribution < 0 ? `−${formatMoney(-piece.contribution)}` : formatMoney(piece.contribution)}
      </span>
      <Icon name="chevronRight" size={14} className="piece__chevron" />
    </button>
  );
}
