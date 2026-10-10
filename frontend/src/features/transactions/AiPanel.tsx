import { startAiEvaluation, startAiSuggestions, stopAi, type AiStatus } from "../../api/ai";
import { Icon } from "../../components/Icon";

interface AiPanelProps {
  status: AiStatus | null;
  error: string | null;
  onRun: (action: () => Promise<AiStatus>) => void;
  onCheckAgain: () => void;
  onClose: () => void;
}

/** Starts, stops and reports the AI's background jobs: suggesting for unclassified transactions, and testing it. */
export function AiPanel({ status, error, onRun, onCheckAgain, onClose }: AiPanelProps) {
  if (status === null) {
    return <p className="form__hint">{error ?? "Checking the AI…"}</p>;
  }

  return (
    <div className="form">
      {!status.available ? (
        <Setup status={status} onCheckAgain={onCheckAgain} />
      ) : (
        <>
          {status.problem !== null && status.working === null && (
            <div className="callout callout--error" role="alert">
              <Icon name="alert" className="callout__icon" />
              <div>
                <p className="callout__title">The last run stopped early</p>
                <p className="callout__text">{status.problem}</p>
              </div>
            </div>
          )}

          <section className="ai__section">
            <h3 className="ai__heading">Suggest categories</h3>
            <p className="form__hint">
              {status.model} reads each unclassified transaction with your categories and your past choices for
              similar ones, then suggests one. Suggestions are never saved until you accept them.
            </p>
            {status.working === "SUGGESTING" ? (
              <div className="ai__progress">
                <span className="ai__pulse" aria-hidden="true" />
                <span>
                  Suggested {status.suggestedThisRun} so far · {status.waiting} left
                </span>
                <button type="button" className="button button--ghost button--sm" onClick={() => onRun(stopAi)}>
                  Stop
                </button>
              </div>
            ) : (
              <button
                type="button"
                className="button"
                disabled={status.working !== null || status.waiting === 0}
                onClick={() => onRun(startAiSuggestions)}
              >
                <Icon name="spark" />
                {status.waiting === 0 ? "Every transaction has a suggestion or a category" : `Suggest for ${status.waiting}`}
              </button>
            )}
          </section>

          <section className="ai__section">
            <h3 className="ai__heading">Test the AI</h3>
            <p className="form__hint">
              Takes your 50 most recently classified transactions, hides your choices, and asks the model. Each one
              only sees examples from before its date, as if it had just been imported.
            </p>
            {status.evaluation && <EvaluationReport status={status} />}
            {status.working === "EVALUATING" ? (
              <button type="button" className="button button--ghost button--sm" onClick={() => onRun(stopAi)}>
                Stop the test
              </button>
            ) : (
              <button
                type="button"
                className="button button--secondary"
                disabled={status.working !== null}
                onClick={() => onRun(startAiEvaluation)}
              >
                {status.evaluation ? "Test again" : "Test it on my transactions"}
              </button>
            )}
          </section>
        </>
      )}

      {error !== null && (
        <p className="form__error" role="alert">
          {error}
        </p>
      )}

      <div className="dialog__actions">
        <button type="button" className="button button--secondary" onClick={onClose}>
          Close
        </button>
      </div>
    </div>
  );
}

function Setup({ status, onCheckAgain }: { status: AiStatus; onCheckAgain: () => void }) {
  return (
    <>
      <div className="callout callout--info">
        <Icon name="info" className="callout__icon" />
        <div>
          <p className="callout__title">The AI isn't ready</p>
          <p className="callout__text">{status.problem}</p>
        </div>
      </div>
      <p className="form__hint">
        Lekha uses a language model running on this computer through Ollama, so your transactions never leave it. To
        set it up once:
      </p>
      <pre className="ai__commands">
        {`brew install ollama\nollama serve          # leave running, or open the Ollama app\nollama pull ${status.model}`}
      </pre>
      <button type="button" className="button button--secondary" onClick={onCheckAgain}>
        <Icon name="refresh" /> Check again
      </button>
    </>
  );
}

function EvaluationReport({ status }: { status: AiStatus }) {
  const evaluation = status.evaluation;
  if (evaluation === null) return null;
  if (evaluation.total === 0) {
    return <p className="form__hint">Classify some transactions first, so there's something to test it against.</p>;
  }

  const running = status.working === "EVALUATING";
  const percent = evaluation.checked === 0 ? null : Math.round((evaluation.correct / evaluation.checked) * 100);
  return (
    <div className="ai__report">
      <p className="ai__score">
        {percent === null ? "–" : `${percent}%`}
        <span className="ai__score-detail">
          {evaluation.correct} of {evaluation.checked} right
          {running
            ? ` · testing ${evaluation.checked} of ${evaluation.total}…`
            : !evaluation.finished && " · stopped before the end"}
          {` · ${evaluation.model}`}
        </span>
      </p>
      {evaluation.misses.length > 0 && (
        <div className="table-wrap">
          <table className="table">
            <thead>
              <tr>
                <th scope="col">Transaction</th>
                <th scope="col">You chose</th>
                <th scope="col">AI chose</th>
              </tr>
            </thead>
            <tbody>
              {evaluation.misses.map((miss, index) => (
                <tr key={index}>
                  <td>
                    <span className="table__narration" title={miss.description}>
                      {miss.description}
                    </span>
                  </td>
                  <td>{miss.yourChoice}</td>
                  <td title={miss.reason}>{miss.aiChoice}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  );
}
