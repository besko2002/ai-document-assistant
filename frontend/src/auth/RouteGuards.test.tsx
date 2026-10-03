import { render, screen, waitFor } from '@testing-library/react';
import { StrictMode } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { describe, expect, it } from 'vitest';
import { App } from '../App';
import { writeToken } from '../api/token';
import { AuthProvider } from './AuthProvider';
import { installFetchMock, renderApp } from '../test/helpers';

describe('route guards', () => {
  it('redirects to the login page when nobody is signed in', async () => {
    const mock = installFetchMock({});

    renderApp('/');

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    expect(mock.spy).not.toHaveBeenCalled();
  });

  it('redirects a deep document link to login when signed out', async () => {
    installFetchMock({});

    renderApp('/documents/doc-1');

    expect(await screen.findByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
  });

  it('clears the session and shows a session-expired notice when /me returns 401', async () => {
    writeToken('expired-token');
    installFetchMock({
      'GET /api/auth/me': { status: 401, body: { status: 401, message: 'Token expired' } },
    });

    renderApp('/');

    expect(await screen.findByText(/session expired/i)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Sign in' })).toBeInTheDocument();
    await waitFor(() => {
      expect(window.localStorage.getItem('ada.accessToken')).toBeNull();
    });
  });

  it('keeps a signed-in visitor away from the login page', async () => {
    writeToken('good-token');
    installFetchMock({
      'GET /api/auth/me': { body: { id: 'u1', name: 'Ada Lovelace', email: 'ada@example.com' } },
      'GET /api/documents': { body: [] },
    });

    renderApp('/login');

    expect(await screen.findByText('Ada Lovelace')).toBeInTheDocument();
    expect(screen.queryByRole('heading', { name: 'Sign in' })).not.toBeInTheDocument();
  });

  it('restores the session on a reload even when React StrictMode runs the effects twice', async () => {
    writeToken('stored-token');
    const mock = installFetchMock({
      'GET /api/auth/me': { body: { id: 'u1', name: 'Ada Lovelace', email: 'ada@example.com' } },
      'GET /api/documents': { body: [] },
    });

    // Regression: dev mode wraps the app in StrictMode, and the session check used to be skipped on the
    // second effect run, leaving "Restoring your session..." on screen forever.
    render(
      <StrictMode>
        <MemoryRouter initialEntries={['/']}>
          <AuthProvider>
            <App />
          </AuthProvider>
        </MemoryRouter>
      </StrictMode>,
    );

    expect(await screen.findByText(/Ada Lovelace/)).toBeInTheDocument();
    expect(screen.queryByText(/restoring your session/i)).not.toBeInTheDocument();
    expect(mock.spy).toHaveBeenCalled();
  });
});
