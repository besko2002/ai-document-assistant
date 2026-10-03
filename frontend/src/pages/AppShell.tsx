import { useCallback, useEffect, useState } from 'react';
import { Outlet, useMatch, useNavigate } from 'react-router-dom';
import { api } from '../api/api';
import type { DocumentSummary } from '../api/types';
import { useAuth } from '../auth/useAuth';
import { Banner } from '../components/Banner';
import { DocumentList } from '../components/DocumentList';
import { UploadZone } from '../components/UploadZone';
import { friendlyError } from '../lib/messages';

function byNewestFirst(a: DocumentSummary, b: DocumentSummary): number {
  return Date.parse(b.createdAt) - Date.parse(a.createdAt);
}

export function AppShell(): JSX.Element {
  const { user, logout } = useAuth();
  const navigate = useNavigate();
  const match = useMatch('/documents/:id');
  const openDocumentId = match?.params.id ?? null;

  const [documents, setDocuments] = useState<DocumentSummary[]>([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [deletingId, setDeletingId] = useState<string | null>(null);
  const [sidebarOpen, setSidebarOpen] = useState(false);

  useEffect(() => {
    const controller = new AbortController();
    let cancelled = false;

    api
      .listDocuments(controller.signal)
      .then((list) => {
        if (!cancelled) {
          setDocuments([...list].sort(byNewestFirst));
          setLoading(false);
        }
      })
      .catch((cause: unknown) => {
        if (!cancelled && !(cause instanceof Error && cause.name === 'AbortError')) {
          setError(friendlyError(cause));
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, []);

  const handleUploaded = useCallback(
    (created: DocumentSummary): void => {
      setDocuments((previous) => [created, ...previous].sort(byNewestFirst));
      setSidebarOpen(false);
      navigate(`/documents/${created.id}`);
    },
    [navigate],
  );

  const handleDelete = useCallback(
    (document: DocumentSummary): void => {
      setDeletingId(document.id);
      setError(null);
      api
        .deleteDocument(document.id)
        .then(() => {
          setDocuments((previous) => previous.filter((item) => item.id !== document.id));
          if (openDocumentId === document.id) {
            navigate('/', { replace: true });
          }
        })
        .catch((cause: unknown) => {
          setError(friendlyError(cause));
        })
        .finally(() => {
          setDeletingId(null);
        });
    },
    [navigate, openDocumentId],
  );

  return (
    <div className="app">
      <header className="topbar">
        <button
          type="button"
          className="topbar__menu"
          aria-expanded={sidebarOpen}
          aria-controls="sidebar"
          onClick={() => {
            setSidebarOpen((open) => !open);
          }}
        >
          <span aria-hidden="true">☰</span> Documents
        </button>

        <p className="brand">
          <span className="brand__mark" aria-hidden="true">
            AI
          </span>
          AI Document Assistant
        </p>

        <div className="topbar__user">
          <span className="topbar__name">{user?.name ?? ''}</span>
          <button type="button" className="button button--ghost" onClick={logout}>
            Log out
          </button>
        </div>
      </header>

      <div className="app__body">
        <aside
          id="sidebar"
          className={`sidebar${sidebarOpen ? ' sidebar--open' : ''}`}
          aria-label="Your documents"
        >
          <UploadZone onUploaded={handleUploaded} />
          <h2 className="sidebar__title">Documents</h2>
          <Banner
            tone="error"
            message={error}
            onDismiss={() => {
              setError(null);
            }}
          />
          <DocumentList
            documents={documents}
            loading={loading}
            deletingId={deletingId}
            onDelete={handleDelete}
            onNavigate={() => {
              setSidebarOpen(false);
            }}
          />
        </aside>

        <main className="main" id="main">
          <Outlet />
        </main>
      </div>
    </div>
  );
}
