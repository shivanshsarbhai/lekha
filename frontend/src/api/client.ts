/**
 * A failed API call. `message` is the server's own explanation (ProblemDetail `detail`) when it sent one, so it can
 * be shown to the user as-is. `status` is 0 when the server could not be reached at all.
 */
export class ApiError extends Error {
  readonly status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

interface RequestOptions {
  body?: BodyInit;
  contentType?: string;
  signal?: AbortSignal | undefined;
}

type Method = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

async function request<T>(method: Method, path: string, options: RequestOptions = {}): Promise<T> {
  const headers: Record<string, string> = { Accept: "application/json" };
  if (options.contentType !== undefined) {
    headers["Content-Type"] = options.contentType;
  }

  let response: Response;
  try {
    response = await fetch(`/api/v1${path}`, {
      method,
      headers,
      ...(options.body !== undefined ? { body: options.body } : {}),
      ...(options.signal ? { signal: options.signal } : {}),
    });
  } catch (error: unknown) {
    if (options.signal?.aborted) throw error;
    throw new ApiError(0, "Could not reach the server. Is the backend running?");
  }

  if (!response.ok) {
    throw await toApiError(response, `${method} ${path} failed with HTTP ${response.status}`);
  }

  // 204 No Content has no body, so parsing it as JSON would throw even though the request succeeded.
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

/** Errors from our ApiExceptionHandler are ProblemDetail JSON; anything else falls back to a generic message. */
async function toApiError(response: Response, fallback: string): Promise<ApiError> {
  try {
    const problem = (await response.json()) as { detail?: unknown };
    if (typeof problem.detail === "string" && problem.detail !== "") {
      return new ApiError(response.status, problem.detail);
    }
  } catch {
    // Not JSON, e.g. the dev proxy's empty 500 when the backend is down.
  }
  return new ApiError(response.status, fallback);
}

export function apiGet<T>(path: string, signal?: AbortSignal): Promise<T> {
  return request<T>("GET", path, { signal });
}

export function apiPost<TBody, TResponse>(path: string, body: TBody): Promise<TResponse> {
  return request<TResponse>("POST", path, { body: JSON.stringify(body), contentType: "application/json" });
}

/** Replaces the resource at `path` with `body`. Sending the same body twice leaves the same result. */
export function apiPut<TBody, TResponse>(path: string, body: TBody): Promise<TResponse> {
  return request<TResponse>("PUT", path, { body: JSON.stringify(body), contentType: "application/json" });
}

/** Changes only the fields in `body`. */
export function apiPatch<TBody, TResponse>(path: string, body: TBody): Promise<TResponse> {
  return request<TResponse>("PATCH", path, { body: JSON.stringify(body), contentType: "application/json" });
}

/** Resolves with no value on 204 No Content. */
export function apiDelete(path: string): Promise<void> {
  return request<void>("DELETE", path);
}

/**
 * Sends a multipart/form-data body, e.g. a file upload. Content-Type is deliberately not set: the browser must
 * write it itself, because only it knows the random boundary string that separates the parts.
 */
export function apiPostForm<TResponse>(path: string, form: FormData): Promise<TResponse> {
  return request<TResponse>("POST", path, { body: form });
}
