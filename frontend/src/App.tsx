import { Link, Navigate, NavLink, Route, Routes } from "react-router";
import { Icon, type IconName } from "./components/Icon";
import { AccountsPage } from "./features/accounts/AccountsPage";
import { CategoriesPage } from "./features/categories/CategoriesPage";
import { DashboardPage } from "./features/dashboard/DashboardPage";
import { SystemStatus } from "./features/system/SystemStatus";
import { TransactionsPage } from "./features/transactions/TransactionsPage";

interface NavEntry {
  label: string;
  icon: IconName;
  to?: string;
}

const NAV: { heading: string; items: NavEntry[] }[] = [
  {
    heading: "Overview",
    items: [
      { label: "Dashboard", icon: "grid", to: "/dashboard" },
      { label: "Transactions", icon: "list", to: "/transactions" },
    ],
  },
  {
    heading: "Set up",
    items: [
      { label: "Accounts", icon: "wallet", to: "/accounts" },
      { label: "Categories", icon: "tag", to: "/categories" },
    ],
  },
  {
    heading: "Coming soon",
    items: [
      { label: "Insights", icon: "chart" },
      { label: "Splits", icon: "split" },
    ],
  },
];

export function App() {
  return (
    <div className="shell">
      <aside className="sidebar">
        <Link to="/dashboard" className="brand">
          <span className="brand__mark" aria-hidden="true">
            ल
          </span>
          <div>
            <span className="brand__name">Lekha</span>
            <span className="brand__tagline">Where your money went</span>
          </div>
        </Link>

        <nav className="nav" aria-label="Main">
          {NAV.map((group) => (
            <div key={group.heading} className="nav__group">
              <span className="nav__heading">{group.heading}</span>
              {group.items.map((item) =>
                item.to ? (
                  <NavLink
                    key={item.label}
                    to={item.to}
                    className={({ isActive }) => `nav__item${isActive ? " nav__item--active" : ""}`}
                  >
                    <Icon name={item.icon} />
                    <span>{item.label}</span>
                  </NavLink>
                ) : (
                  <span key={item.label} className="nav__item nav__item--disabled" aria-disabled="true">
                    <Icon name={item.icon} />
                    <span>{item.label}</span>
                    <span className="nav__soon">Soon</span>
                  </span>
                ),
              )}
            </div>
          ))}
        </nav>

        <div className="sidebar__footer">
          <SystemStatus />
        </div>
      </aside>

      <main className="main">
        <Routes>
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/accounts" element={<AccountsPage />} />
          <Route path="/transactions" element={<TransactionsPage />} />
          <Route path="/categories" element={<CategoriesPage />} />
          <Route path="*" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </main>
    </div>
  );
}
