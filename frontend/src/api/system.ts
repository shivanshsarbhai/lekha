import { apiGet } from "./client";

export interface SystemInfo {
  name: string;
  version: string;
  /** ISO-8601 instant; JSON has no Date type, so parse it where it is displayed. */
  serverTime: string;
}

export function getSystemInfo(signal?: AbortSignal): Promise<SystemInfo> {
  return apiGet<SystemInfo>("/system/info", signal);
}
