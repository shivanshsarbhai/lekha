import { apiGet, apiPost } from "./client";

/** A transaction the model got wrong while being tested. Mirrors AiClassifier.Miss. */
export interface AiMiss {
  description: string;
  yourChoice: string;
  aiChoice: string;
  reason: string;
}

/** Mirrors AiClassifier.Evaluation: `correct` of the `checked` so far matched your own choice. */
export interface AiEvaluation {
  model: string;
  total: number;
  checked: number;
  correct: number;
  finished: boolean;
  misses: AiMiss[];
}

/** Mirrors AiClassifier.Status. */
export interface AiStatus {
  model: string;
  /** Ollama is running and has the model downloaded. */
  available: boolean;
  /** Why it isn't available, or why the last job stopped early. Written for the user to act on. */
  problem: string | null;
  working: "SUGGESTING" | "EVALUATING" | null;
  /** Unclassified transactions the model hasn't looked at yet. */
  waiting: number;
  suggestedThisRun: number;
  evaluation: AiEvaluation | null;
}

export function getAiStatus(signal?: AbortSignal): Promise<AiStatus> {
  return apiGet<AiStatus>("/ai/status", signal);
}

/** Returns at once; the model works in the background. Poll the status to follow it. */
export function startAiSuggestions(): Promise<AiStatus> {
  return apiPost<null, AiStatus>("/ai/suggestions", null);
}

export function startAiEvaluation(): Promise<AiStatus> {
  return apiPost<null, AiStatus>("/ai/evaluation", null);
}

export function stopAi(): Promise<AiStatus> {
  return apiPost<null, AiStatus>("/ai/stop", null);
}
