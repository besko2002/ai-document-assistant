import { useState } from 'react';
import { NavLink } from 'react-router-dom';
import type { DocumentSummary } from '../api/types';
import { formatRelativeDate, pluralise } from '../lib/format';

interface DocumentListProps {
  documents: DocumentSummary[];
  loading: boolean;
  deletingId: string | null;
  onDelete: (document: DocumentSummary) => void;
  onNavigate: () => void;
}

export function DocumentList({
  documents,
  loading,
  deletingId,
  onDelete,
  onNavigate,
}: DocumentListProps): JSX.Element {
  const [confirmId, setConfirmId] = useState<string | null>(null);

  if (loading) {
    return (
      <p className="sidebar__hint" role="status">
        Loading your documents…
      </p>
    );
  }

  if (documents.length === 0) {
    return (
      <div className="empty empty--sidebar">
        <p className="empty__title">No documents yet</p>
        <p className="empty__text">Upload a PDF to start asking questions about it.</p>
      </div>
    );
  }

  return (
    <ul className="doc-list">
      {documents.map((document) => {
        const confirming = confirmId === document.id;
        const deleting = deletingId === document.id;
        return (
          <li key={document.id} className="doc-list__item">
            <NavLink
              to={`/documents/${document.id}`}
              className={({ isActive }) => `doc-card${isActive ? ' doc-card--active' : ''}`}
              onClick={onNavigate}
            >
              <span className="doc-card__name" title={document.filename}>
                {document.filename}
              </span>
              <span className="doc-card__meta">
                {pluralise(document.pageCount, 'page')} · {formatRelativeDate(document.createdAt)}
              </span>
            </NavLink>

            {confirming ? (
              <div className="doc-card__confirm">
                <span>Delete this document?</span>
                <button
                  type="button"
                  className="button button--danger button--small"
                  disabled={deleting}
                  onClick={() => {
                    setConfirmId(null);
                    onDelete(document);
                  }}
                >
                  {deleting ? 'Deleting…' : 'Delete'}
                </button>
                <button
                  type="button"
                  className="button button--ghost button--small"
                  onClick={() => {
                    setConfirmId(null);
                  }}
                >
                  Cancel
                </button>
              </div>
            ) : (
              <button
                type="button"
                className="doc-card__delete"
                aria-label={`Delete ${document.filename}`}
                onClick={() => {
                  setConfirmId(document.id);
                }}
              >
                ×
              </button>
            )}
          </li>
        );
      })}
    </ul>
  );
}
