import { Navigate, Outlet, useLocation } from 'react-router-dom';
import { useAuth } from './useAuth';

function SessionLoading(): JSX.Element {
  return (
    <div className="centered-page" role="status" aria-live="polite">
      <span className="spinner" aria-hidden="true" />
      <p>Restoring your session…</p>
    </div>
  );
}

/** Blocks the app shell until we know the visitor is signed in. */
export function ProtectedRoute(): JSX.Element {
  const { status } = useAuth();
  const location = useLocation();

  if (status === 'loading') {
    return <SessionLoading />;
  }
  if (status === 'anonymous') {
    return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  }
  return <Outlet />;
}

/** Keeps signed-in users away from /login and /register. */
export function PublicOnlyRoute(): JSX.Element {
  const { status } = useAuth();

  if (status === 'loading') {
    return <SessionLoading />;
  }
  if (status === 'authenticated') {
    return <Navigate to="/" replace />;
  }
  return <Outlet />;
}
