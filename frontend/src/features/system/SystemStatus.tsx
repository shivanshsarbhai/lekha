import { useSystemInfo } from "./useSystemInfo";

export function SystemStatus() {
  const state = useSystemInfo();

  switch (state.status) {
    case "loading":
      return (
        <span className="pill">
          <span className="pill__dot" /> Connecting…
        </span>
      );
    case "error":
      return (
        <span className="pill pill--error" title={state.message}>
          <span className="pill__dot" /> Backend offline
        </span>
      );
    case "success":
      return (
        <span
          className="pill pill--ok"
          title={`Server time ${new Date(state.data.serverTime).toLocaleString()}`}
        >
          <span className="pill__dot" /> API v{state.data.version}
        </span>
      );
  }
}
