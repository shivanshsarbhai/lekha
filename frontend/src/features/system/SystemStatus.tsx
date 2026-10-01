import { useSystemInfo } from "./useSystemInfo";

export function SystemStatus() {
  const state = useSystemInfo();

  switch (state.status) {
    case "loading":
      return (
        <span className="system-status">
          <span className="status-dot status-dot--pulse" /> Connecting…
        </span>
      );
    case "error":
      return (
        <span className="system-status system-status--error" title={state.message}>
          <span className="status-dot status-dot--error" /> Backend offline
        </span>
      );
    case "success":
      return (
        <span
          className="system-status"
          title={`Server time ${new Date(state.data.serverTime).toLocaleString()}`}
        >
          <span className="status-dot status-dot--ok" /> Connected · v{state.data.version}
        </span>
      );
  }
}
