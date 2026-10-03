import { Navigate, Route, Routes, useParams } from 'react-router-dom';
import { ProtectedRoute, PublicOnlyRoute } from './auth/RouteGuards';
import { AppShell } from './pages/AppShell';
import { AuthPage } from './pages/AuthPage';
import { ChatPanel } from './pages/ChatPanel';
import { NoDocumentSelected } from './pages/NoDocumentSelected';

/** Remounts the chat when the document changes so its state starts clean. */
function ChatPanelRoute(): JSX.Element {
  const { id = '' } = useParams<{ id: string }>();
  return <ChatPanel key={id} />;
}

export function App(): JSX.Element {
  return (
    <Routes>
      <Route element={<PublicOnlyRoute />}>
        <Route path="/login" element={<AuthPage mode="login" />} />
        <Route path="/register" element={<AuthPage mode="register" />} />
      </Route>

      <Route element={<ProtectedRoute />}>
        <Route path="/" element={<AppShell />}>
          <Route index element={<NoDocumentSelected />} />
          <Route path="documents/:id" element={<ChatPanelRoute />} />
        </Route>
      </Route>

      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}
