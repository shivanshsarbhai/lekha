import { SystemStatus } from "./features/system/SystemStatus";

export function App() {
  return (
    <main className="app">
      <h1>Lekha</h1>
      <p className="tagline">Track and understand your money.</p>
      <SystemStatus />
    </main>
  );
}
