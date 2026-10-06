import { useEffect, useState, type CSSProperties } from "react";
import { useSearchParams } from "react-router";
import {
  createCategory,
  deleteCategory,
  renameCategory,
  type CategoryKind,
  type CategoryNode,
} from "../../api/categories";
import { Dialog } from "../../components/Dialog";
import { Icon } from "../../components/Icon";
import { useCategories } from "./useCategories";

const TABS: { kind: CategoryKind; label: string; hint: string }[] = [
  { kind: "EXPENSE", label: "Expenses", hint: "Where your money is actually spent. These add up to your spending." },
  { kind: "INCOME", label: "Income", hint: "Money you earn: salary, interest, freelance work." },
  { kind: "INVESTMENT", label: "Investments", hint: "Money put into, or taken out of, investments." },
  { kind: "TRANSFER", label: "Transfers", hint: "Your own money moving between places. Counted in no total." },
];

const NAME_MAX = 40;

function isKind(value: string | null): value is CategoryKind {
  return TABS.some((tab) => tab.kind === value);
}

function errorMessage(error: unknown): string {
  return error instanceof Error ? error.message : "Unknown error";
}

/** A stable colour per category name, so each card is easy to recognise at a glance. */
function hueOf(name: string): number {
  let hash = 0;
  for (const char of name) hash = (hash * 31 + char.charCodeAt(0)) % 360;
  return hash;
}

type Deleting = { node: CategoryNode; parent: CategoryNode | null };

export function CategoriesPage() {
  const { state, reload } = useCategories();
  const [searchParams, setSearchParams] = useSearchParams();
  const kindParam = searchParams.get("kind");
  const kind: CategoryKind = isKind(kindParam) ? kindParam : "EXPENSE";
  const [query, setQuery] = useState("");
  const [creating, setCreating] = useState(false);
  const [deleting, setDeleting] = useState<Deleting | null>(null);
  const [toast, setToast] = useState<string | null>(null);

  useEffect(() => {
    if (toast === null) return;
    const timer = setTimeout(() => setToast(null), 3000);
    return () => clearTimeout(timer);
  }, [toast]);

  const tree = state.status === "success" ? state.tree : [];
  const tab = TABS.find((candidate) => candidate.kind === kind) ?? TABS[0]!;
  const parents = tree.filter((node) => node.kind === kind);
  const needle = query.trim().toLowerCase();
  const visible = parents.flatMap((parent) => {
    if (needle === "") return [{ parent, children: parent.children }];
    const parentMatches = parent.name.toLowerCase().includes(needle);
    const children = parentMatches
      ? parent.children
      : parent.children.filter((child) => child.name.toLowerCase().includes(needle));
    return parentMatches || children.length > 0 ? [{ parent, children }] : [];
  });

  function switchTab(next: CategoryKind) {
    setCreating(false);
    setQuery("");
    setSearchParams((current) => {
      const params = new URLSearchParams(current);
      params.set("kind", next);
      return params;
    });
  }

  /** Every change goes to the server first; only then is the tree re-fetched, so the page always shows saved state. */
  async function change(action: () => Promise<unknown>, done: string) {
    await action();
    reload();
    setToast(done);
  }

  return (
    <div className="page">
      <header className="page-header">
        <div>
          <p className="page-header__eyebrow">Organise</p>
          <h1 className="page-header__title">Categories</h1>
          <p className="page-header__subtitle">How Lekha groups your money. Click any name to rename it.</p>
        </div>
        <button
          type="button"
          className="button"
          onClick={() => {
            setQuery("");
            setCreating(true);
          }}
        >
          <Icon name="plus" /> New category
        </button>
      </header>

      {state.status === "error" && (
        <div className="callout callout--error" role="alert">
          <Icon name="alert" className="callout__icon" />
          <div>
            <p className="callout__title">Couldn't load categories</p>
            <p className="callout__text">{state.message}</p>
          </div>
        </div>
      )}

      <div className="cat-toolbar">
        <div className="cat-tabs" role="tablist" aria-label="Kind">
          {TABS.map((candidate) => {
            const count = tree
              .filter((node) => node.kind === candidate.kind)
              .reduce((total, node) => total + 1 + node.children.length, 0);
            return (
              <button
                key={candidate.kind}
                type="button"
                role="tab"
                aria-selected={candidate.kind === kind}
                className={`cat-tab${candidate.kind === kind ? " cat-tab--active" : ""}`}
                onClick={() => switchTab(candidate.kind)}
              >
                {candidate.label}
                {state.status === "success" && <span className="cat-tab__count">{count}</span>}
              </button>
            );
          })}
        </div>

        <label className="cat-search">
          <Icon name="search" size={16} />
          <span className="sr-only">Search categories</span>
          <input
            type="search"
            value={query}
            onChange={(event) => setQuery(event.target.value)}
            placeholder={`Search ${tab.label.toLowerCase()}`}
          />
        </label>
      </div>

      <p className="cat-hint">
        <Icon name="info" size={14} /> {tab.hint}
      </p>

      {state.status === "loading" && (
        <div className="cat-grid">
          {[0, 1, 2, 3, 4, 5].map((i) => (
            <div key={i} className="cat-card cat-card--skeleton">
              <span className="skeleton skeleton--line" />
              <span className="skeleton skeleton--line" />
            </div>
          ))}
        </div>
      )}

      {state.status === "success" &&
        (needle !== "" && visible.length === 0 ? (
          <div className="empty">
            <span className="empty__icon">
              <Icon name="search" size={26} />
            </span>
            <p className="empty__title">Nothing called "{query.trim()}"</p>
            <p className="empty__text">Try another word, or add it as a new category.</p>
            <button
              type="button"
              className="button"
              onClick={() => {
                setQuery("");
                setCreating(true);
              }}
            >
              <Icon name="plus" /> New category
            </button>
          </div>
        ) : (
          <div className="cat-grid">
            {visible.map(({ parent, children }) => (
              <CategoryCard
                key={parent.id}
                parent={parent}
                children={children}
                highlight={needle}
                onRename={(node, name) => change(() => renameCategory(node.id, name), `Renamed to "${name.trim()}"`)}
                onAddChild={(name) =>
                  change(
                    () => createCategory({ name, kind: null, parentId: parent.id }),
                    `Added "${name.trim()}" to ${parent.name}`,
                  )
                }
                onDelete={(node) => setDeleting({ node, parent: node.id === parent.id ? null : parent })}
              />
            ))}

            {needle === "" && (
              <div className="cat-card cat-card--new">
                {creating ? (
                  <div className="cat-card__new-form">
                    <p className="cat-card__new-title">New {tab.label.toLowerCase()} category</p>
                    <InlineName
                      placeholder="e.g. Pets"
                      submitLabel="Add"
                      onSubmit={(name) =>
                        change(() => createCategory({ name, kind, parentId: null }), `Added "${name.trim()}"`)
                      }
                      onDone={() => setCreating(false)}
                    />
                  </div>
                ) : (
                  <button type="button" className="cat-card__new-button" onClick={() => setCreating(true)}>
                    <Icon name="plus" size={20} />
                    New {tab.label.toLowerCase()} category
                  </button>
                )}
              </div>
            )}
          </div>
        ))}

      <Dialog
        open={deleting !== null}
        onClose={() => setDeleting(null)}
        title={deleting && deleting.node.children.length > 0 ? `"${deleting.node.name}" can't be deleted yet` : `Delete "${deleting?.node.name ?? ""}"?`}
      >
        {deleting && (
          <DeleteCategory
            deleting={deleting}
            onDeleted={() => {
              const name = deleting.node.name;
              setDeleting(null);
              reload();
              setToast(`Deleted "${name}"`);
            }}
            onCancel={() => setDeleting(null)}
          />
        )}
      </Dialog>

      {toast !== null && (
        <div className="toast" role="status">
          <Icon name="check" size={16} /> {toast}
        </div>
      )}
    </div>
  );
}

interface CategoryCardProps {
  parent: CategoryNode;
  children: CategoryNode[];
  highlight: string;
  onRename: (node: CategoryNode, name: string) => Promise<void>;
  onAddChild: (name: string) => Promise<void>;
  onDelete: (node: CategoryNode) => void;
}

function CategoryCard({ parent, children, highlight, onRename, onAddChild, onDelete }: CategoryCardProps) {
  const [renaming, setRenaming] = useState(false);
  const [adding, setAdding] = useState(false);
  const total = parent.children.length;

  return (
    <article className="cat-card" style={{ "--hue": hueOf(parent.name) } as CSSProperties}>
      <header className="cat-card__header">
        <span className="cat-avatar" aria-hidden="true">
          {parent.name.charAt(0).toUpperCase()}
        </span>
        {renaming ? (
          <div className="cat-card__rename">
            <InlineName
              initial={parent.name}
              submitLabel="Save"
              onSubmit={(name) => onRename(parent, name)}
              onDone={() => setRenaming(false)}
            />
          </div>
        ) : (
          <>
            <button
              type="button"
              className="cat-card__title"
              onClick={() => setRenaming(true)}
              title="Click to rename"
            >
              <span className="cat-card__name">
                <Highlight text={parent.name} needle={highlight} />
              </span>
              <span className="cat-card__meta">
                {total === 0 ? "No sub-categories" : `${total} sub-categor${total === 1 ? "y" : "ies"}`}
              </span>
            </button>
            <div className="cat-card__actions">
              <button
                type="button"
                className="icon-button"
                aria-label={`Rename ${parent.name}`}
                title="Rename"
                onClick={() => setRenaming(true)}
              >
                <Icon name="edit" size={16} />
              </button>
              <button
                type="button"
                className="icon-button icon-button--danger"
                aria-label={`Delete ${parent.name}`}
                title="Delete"
                onClick={() => onDelete(parent)}
              >
                <Icon name="trash" size={16} />
              </button>
            </div>
          </>
        )}
      </header>

      <ul className="cat-chips" aria-label={`Sub-categories of ${parent.name}`}>
        {children.map((child) => (
          <li key={child.id}>
            <CategoryChip
              node={child}
              highlight={highlight}
              onRename={(name) => onRename(child, name)}
              onDelete={() => onDelete(child)}
            />
          </li>
        ))}
        <li className={adding ? "cat-chips__adding" : undefined}>
          {adding ? (
            <InlineName
              placeholder={`New in ${parent.name}`}
              submitLabel="Add"
              compact
              keepOpen
              onSubmit={onAddChild}
              onDone={() => setAdding(false)}
            />
          ) : (
            <button type="button" className="cat-chip cat-chip--add" onClick={() => setAdding(true)}>
              <Icon name="plus" size={13} /> Add
            </button>
          )}
        </li>
      </ul>
    </article>
  );
}

interface CategoryChipProps {
  node: CategoryNode;
  highlight: string;
  onRename: (name: string) => Promise<void>;
  onDelete: () => void;
}

function CategoryChip({ node, highlight, onRename, onDelete }: CategoryChipProps) {
  const [renaming, setRenaming] = useState(false);

  if (renaming) {
    return (
      <InlineName initial={node.name} submitLabel="Save" compact onSubmit={onRename} onDone={() => setRenaming(false)} />
    );
  }

  return (
    <span className="cat-chip">
      <button type="button" className="cat-chip__label" onClick={() => setRenaming(true)} title="Click to rename">
        <Highlight text={node.name} needle={highlight} />
      </button>
      <button
        type="button"
        className="cat-chip__remove"
        aria-label={`Delete ${node.name}`}
        title="Delete"
        onClick={onDelete}
      >
        <Icon name="close" size={12} />
      </button>
    </span>
  );
}

interface InlineNameProps {
  initial?: string;
  placeholder?: string;
  submitLabel: string;
  compact?: boolean;
  /** Stays open after a successful save, ready for the next name. For adding several in a row. */
  keepOpen?: boolean;
  onSubmit: (name: string) => Promise<void>;
  onDone: () => void;
}

/** A name field that saves on Enter and cancels on Escape, showing the server's reason if the save is refused. */
function InlineName({ initial = "", placeholder, submitLabel, compact = false, keepOpen = false, onSubmit, onDone }: InlineNameProps) {
  const [name, setName] = useState(initial);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function handleSubmit(event: { preventDefault(): void }) {
    event.preventDefault();
    if (initial !== "" && name.trim() === initial) {
      onDone();
      return;
    }
    setSaving(true);
    setError(null);
    try {
      await onSubmit(name);
      if (keepOpen) {
        setName("");
        setSaving(false);
      } else {
        onDone();
      }
    } catch (caught: unknown) {
      setError(errorMessage(caught));
      setSaving(false);
    }
  }

  return (
    <form className={`inline-name${compact ? " inline-name--compact" : ""}`} onSubmit={handleSubmit}>
      <div className="inline-name__row">
        <input
          className="input"
          value={name}
          onChange={(event) => {
            setName(event.target.value);
            setError(null);
          }}
          onKeyDown={(event) => {
            if (event.key === "Escape") {
              event.stopPropagation();
              onDone();
            }
          }}
          placeholder={placeholder}
          maxLength={NAME_MAX}
          aria-invalid={error !== null}
          autoFocus
          onFocus={(event) => event.target.select()}
        />
        <button type="submit" className="button button--sm" disabled={name.trim() === "" || saving}>
          {saving ? "…" : submitLabel}
        </button>
        <button type="button" className="icon-button" aria-label="Cancel" title="Cancel (Esc)" onClick={onDone}>
          <Icon name="close" size={16} />
        </button>
      </div>
      {error !== null && (
        <p className="inline-name__error" role="alert">
          {error}
        </p>
      )}
    </form>
  );
}

function Highlight({ text, needle }: { text: string; needle: string }) {
  const index = needle === "" ? -1 : text.toLowerCase().indexOf(needle);
  if (index < 0) return <>{text}</>;
  return (
    <>
      {text.slice(0, index)}
      <mark>{text.slice(index, index + needle.length)}</mark>
      {text.slice(index + needle.length)}
    </>
  );
}

function DeleteCategory({ deleting, onDeleted, onCancel }: { deleting: Deleting; onDeleted: () => void; onCancel: () => void }) {
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const { node, parent } = deleting;
  const childCount = node.children.length;

  if (childCount > 0) {
    return (
      <div className="form">
        <p className="dialog__text">
          It still has {childCount} sub-categor{childCount === 1 ? "y" : "ies"}:{" "}
          <strong>{node.children.map((child) => child.name).join(", ")}</strong>. Delete or rename those first, so nothing
          is left without a home.
        </p>
        <div className="dialog__actions">
          <button type="button" className="button" onClick={onCancel}>
            Got it
          </button>
        </div>
      </div>
    );
  }

  async function handleDelete() {
    setSaving(true);
    try {
      await deleteCategory(node.id);
      onDeleted();
    } catch (caught: unknown) {
      setError(errorMessage(caught));
      setSaving(false);
    }
  }

  return (
    <div className="form">
      <p className="dialog__text">
        {parent ? (
          <>
            This removes it from <strong>{parent.name}</strong>.{" "}
          </>
        ) : null}
        You can't undo this. If any transaction is classified under it, Lekha keeps it and tells you.
      </p>

      {error !== null && (
        <p className="form__error" role="alert">
          {error}
        </p>
      )}

      <div className="dialog__actions">
        <button type="button" className="button button--secondary" onClick={onCancel}>
          {error !== null ? "Close" : "Cancel"}
        </button>
        {error === null && (
          <button type="button" className="button button--danger" disabled={saving} onClick={() => void handleDelete()}>
            {saving ? "Deleting…" : "Delete"}
          </button>
        )}
      </div>
    </div>
  );
}
