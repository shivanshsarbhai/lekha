import { useEffect, useState } from "react";
import { getSystemInfo, type SystemInfo } from "../../api/system";

export type SystemInfoState =
  | { status: "loading" }
  | { status: "success"; data: SystemInfo }
  | { status: "error"; message: string };

export function useSystemInfo(): SystemInfoState {
  const [state, setState] = useState<SystemInfoState>({ status: "loading" });

  useEffect(() => {
    const controller = new AbortController();

    getSystemInfo(controller.signal)
      .then((data) => setState({ status: "success", data }))
      .catch((error: unknown) => {
        if (controller.signal.aborted) return;
        const message = error instanceof Error ? error.message : "Unknown error";
        setState({ status: "error", message });
      });

    return () => controller.abort();
  }, []);

  return state;
}
