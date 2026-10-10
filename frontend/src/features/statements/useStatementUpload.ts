import { useCallback, useState } from "react";
import { ApiError } from "../../api/client";
import { uploadStatement, type StatementImport } from "../../api/statements";

export type UploadState =
  | { status: "idle" }
  | { status: "uploading"; fileName: string }
  | { status: "done"; fileName: string; result: StatementImport }
  | { status: "failed"; fileName: string; message: string }
  /** The PDF is password-protected. It is kept here so the user only types the password, not picks the file again. */
  | { status: "locked"; file: File; message: string; wrongPassword: boolean };

export function useStatementUpload(accountId: string) {
  const [state, setState] = useState<UploadState>({ status: "idle" });

  const upload = useCallback(
    async (file: File, password?: string): Promise<StatementImport | null> => {
      setState({ status: "uploading", fileName: file.name });
      try {
        const result = await uploadStatement(accountId, file, password);
        setState({ status: "done", fileName: file.name, result });
        return result;
      } catch (error: unknown) {
        if (error instanceof ApiError && (error.code === "PASSWORD_REQUIRED" || error.code === "WRONG_PASSWORD")) {
          setState({ status: "locked", file, message: error.message, wrongPassword: error.code === "WRONG_PASSWORD" });
          return null;
        }
        const message = error instanceof Error ? error.message : "Unknown error";
        setState({ status: "failed", fileName: file.name, message });
        return null;
      }
    },
    [accountId],
  );

  const reset = useCallback(() => setState({ status: "idle" }), []);

  return { state, upload, reset };
}
