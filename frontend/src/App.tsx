import { AccountsPage } from "./features/accounts/AccountsPage";
import { SystemStatus } from "./features/system/SystemStatus";

export function App() {
  return (
    <div className="shell">
      <header className="topbar">
        <div className="topbar__inner">
          <div className="brand">
            <span className="brand__mark" aria-hidden="true">
              ल
            </span>
            <span className="brand__name">Lekha</span>
          </div>
          <SystemStatus />
        </div>
      </header>
      <main className="content">
        <AccountsPage />
      </main>
    </div>
  );
}
