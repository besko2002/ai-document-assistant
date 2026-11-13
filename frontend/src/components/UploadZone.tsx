import { useRef, useState, type ChangeEvent, type DragEvent } from 'react';
import { api } from '../api/api';
import type { DocumentSummary } from '../api/types';
import { friendlyError } from '../lib/messages';
import { validatePdfFile } from '../lib/upload';

type UploadState =
  | { kind: 'idle' }
  | { kind: 'uploading'; percent: number }
  | { kind: 'processing' };

interface UploadZoneProps {
  onUploaded: (document: DocumentSummary) => void;
}

export function UploadZone({ onUploaded }: UploadZoneProps): JSX.Element {
  const inputRef = useRef<HTMLInputElement>(null);
  const [state, setState] = useState<UploadState>({ kind: 'idle' });
  const [error, setError] = useState<string | null>(null);
  const [dragging, setDragging] = useState(false);

  const busy = state.kind !== 'idle';

  async function upload(file: File): Promise<void> {
    const problem = validatePdfFile(file);
    if (problem !== null) {
      setError(problem);
      return;
    }

    setError(null);
    setState({ kind: 'uploading', percent: 0 });
    try {
      const created = await api.uploadDocument(file, (percent) => {
        setState(percent >= 100 ? { kind: 'processing' } : { kind: 'uploading', percent });
      });
      setState({ kind: 'idle' });
      onUploaded(created);
    } catch (cause) {
      setState({ kind: 'idle' });
      setError(friendlyError(cause));
    }
  }

  function handleChange(event: ChangeEvent<HTMLInputElement>): void {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (file !== undefined && !busy) {
      void upload(file);
    }
  }

  function handleDrop(event: DragEvent<HTMLDivElement>): void {
    event.preventDefault();
    setDragging(false);
    const file = event.dataTransfer.files[0];
    if (file !== undefined && !busy) {
      void upload(file);
    }
  }

  const statusText =
    state.kind === 'uploading'
      ? `Uploading… ${String(state.percent)}%`
      : state.kind === 'processing'
        ? 'Processing… extracting text and building embeddings. This takes a few seconds.'
        : null;

  return (
    <div className="uploader">
      <div
        role="group"
        aria-label="Upload a PDF"
        className={`dropzone${dragging ? ' dropzone--active' : ''}${busy ? ' dropzone--busy' : ''}`}
        onDragOver={(event) => {
          event.preventDefault();
          setDragging(true);
        }}
        onDragLeave={() => {
          setDragging(false);
        }}
        onDrop={handleDrop}
      >
        <label className="sr-only" htmlFor="pdf-file-input">
          PDF file
        </label>
        <input
          id="pdf-file-input"
          ref={inputRef}
          className="sr-only"
          type="file"
          accept="application/pdf,.pdf"
          onChange={handleChange}
          disabled={busy}
        />
        <button
          type="button"
          className="button button--primary button--block"
          onClick={() => {
            inputRef.current?.click();
          }}
          disabled={busy}
        >
          {busy ? 'Uploading…' : 'Upload PDF'}
        </button>
        <p className="dropzone__hint">or drop a PDF here · max 25 MB</p>
      </div>

      <div className="uploader__status" role="status" aria-live="polite">
        {statusText !== null && (
          <>
            <p className="uploader__status-text">{statusText}</p>
            <div
              className="progress"
              role="progressbar"
              aria-label="Upload progress"
              aria-valuemin={0}
              aria-valuemax={100}
              aria-valuenow={state.kind === 'uploading' ? state.percent : 100}
            >
              <span
                className={`progress__bar${state.kind === 'processing' ? ' progress__bar--pulse' : ''}`}
                style={{ width: state.kind === 'uploading' ? `${String(state.percent)}%` : '100%' }}
              />
            </div>
          </>
        )}
      </div>

      <div className="uploader__error" role="alert" aria-live="assertive">
        {error !== null && <p className="field__error">{error}</p>}
      </div>
    </div>
  );
}
