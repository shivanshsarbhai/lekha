import { useCallback, useState } from "react";
import { uploadStatement, type StatementImport } from "../../api/statements";

export type UploadState =
  | { status: "idle" }
  | { status: "uploading"; fileName: string }
  | { status: "done"; fileName: string; result: StatementImport }
  | { status: "failed"; fileName: string; message: string };

export function useStatementUpload(accountId: string) {
  const [state, setState] = useState<UploadState>({ status: "idle" });

  const upload = useCallback(
    async (file: File): Promise<StatementImport | null> => {
      setState({ status: "uploading", fileName: file.name });
      try {
        const result = await uploadStatement(accountId, file);
        setState({ status: "done", fileName: file.name, result });
        return result;
      } catch (error: unknown) {
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
