import { useSystemInfo } from "./useSystemInfo";

export function SystemStatus() {
  const state = useSystemInfo();

  switch (state.status) {
    case "loading":
      return <p className="status">Connecting to backend…</p>;
    case "error":
      return (
        <p className="status status--error">
          Backend unreachable: {state.message}. Is <code>./gradlew bootRun</code> running?
        </p>
      );
    case "success":
      return (
        <p className="status status--ok">
          Connected to {state.data.name} API v{state.data.version} · server time{" "}
          {new Date(state.data.serverTime).toLocaleString()}
        </p>
      );
  }
}
