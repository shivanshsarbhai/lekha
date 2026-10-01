import { Icon, type IconName } from "./components/Icon";
import { AccountsPage } from "./features/accounts/AccountsPage";
import { SystemStatus } from "./features/system/SystemStatus";

const NAV: { label: string; icon: IconName; active?: boolean }[] = [
  { label: "Accounts", icon: "wallet", active: true },
  { label: "Transactions", icon: "list" },
  { label: "Insights", icon: "chart" },
  { label: "Splits", icon: "split" },
];

export function App() {
  return (
    <div className="shell">
      <aside className="sidebar">
        <div className="brand">
          <span className="brand__mark" aria-hidden="true">
            ल
          </span>
          <div>
            <span className="brand__name">Lekha</span>
            <span className="brand__tagline">Where your money went</span>
          </div>
        </div>

        <nav className="nav" aria-label="Main">
          {NAV.map((item) => (
            <button
              key={item.label}
              type="button"
              className={`nav__item${item.active ? " nav__item--active" : ""}`}
              aria-current={item.active ? "page" : undefined}
              disabled={!item.active}
            >
              <Icon name={item.icon} />
              <span>{item.label}</span>
              {!item.active && <span className="nav__soon">Soon</span>}
            </button>
          ))}
        </nav>

        <div className="sidebar__footer">
          <SystemStatus />
        </div>
      </aside>

      <main className="main">
        <AccountsPage />
      </main>
    </div>
  );
}
