import type { Source } from '../api/types';
import { truncate } from '../lib/format';

/** Collapsible "Page N — snippet (score)" cards shown under each answer. */
export function SourceList({ sources }: { sources: Source[] }): JSX.Element | null {
  if (sources.length === 0) {
    return null;
  }

  return (
    <div className="sources">
      <p className="sources__label">Sources</p>
      {sources.map((source, index) => (
        <details className="source" key={`${String(source.page)}-${String(index)}`}>
          <summary className="source__summary">
            <span className="source__page">Page {source.page}</span>
            <span className="source__preview"> — {truncate(source.snippet, 70)}</span>
            <span className="source__score"> ({source.score.toFixed(2)})</span>
          </summary>
          <p className="source__snippet">{source.snippet}</p>
        </details>
      ))}
    </div>
  );
}
