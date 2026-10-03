import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it } from 'vitest';
import { installFetchMock, renderApp } from '../test/helpers';

const SESSION = {
  accessToken: 'jwt-abc',
  tokenType: 'Bearer',
  expiresIn: 3600,
  user: { id: 'u1', name: 'Ada Lovelace', email: 'ada@example.com' },
};

describe('login and register forms', () => {
  it('blocks submission and shows client-side errors for invalid input', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock({});
    renderApp('/login');

    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText(/enter your email address/i)).toBeInTheDocument();
    expect(screen.getByText(/enter your password/i)).toBeInTheDocument();
    expect(mock.spy).not.toHaveBeenCalled();
  });

  it('shows the server message when credentials are rejected', async () => {
    const user = userEvent.setup();
    installFetchMock({
      'POST /api/auth/login': {
        status: 401,
        body: { status: 401, message: 'Invalid email or password' },
      },
    });
    renderApp('/login');

    await user.type(screen.getByLabelText('Email'), 'ada@example.com');
    await user.type(screen.getByLabelText('Password'), 'wrongpassword');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    expect(await screen.findByText('Invalid email or password')).toBeInTheDocument();
    expect(window.localStorage.getItem('ada.accessToken')).toBeNull();
  });

  it('stores the token and redirects to the app shell on success', async () => {
    const user = userEvent.setup();
    installFetchMock({
      'POST /api/auth/login': { body: SESSION },
      'GET /api/documents': { body: [] },
    });
    renderApp('/login');

    await user.type(screen.getByLabelText('Email'), 'ada@example.com');
    await user.type(screen.getByLabelText('Password'), 'password12');
    await user.click(screen.getByRole('button', { name: 'Sign in' }));

    await waitFor(() => {
      expect(window.localStorage.getItem('ada.accessToken')).toBe('jwt-abc');
    });
    expect(await screen.findByText('Ada Lovelace')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Log out' })).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Sign in' })).not.toBeInTheDocument();
  });

  it('surfaces server fieldErrors on the register form', async () => {
    const user = userEvent.setup();
    installFetchMock({
      'POST /api/auth/register': {
        status: 409,
        body: {
          status: 409,
          message: 'Email already registered',
          fieldErrors: { email: 'already in use' },
        },
      },
    });
    renderApp('/register');

    await user.type(screen.getByLabelText('Name'), 'Ada');
    await user.type(screen.getByLabelText('Email'), 'ada@example.com');
    await user.type(screen.getByLabelText('Password'), 'password12');
    await user.click(screen.getByRole('button', { name: 'Create account' }));

    expect(await screen.findByText('Email already registered')).toBeInTheDocument();
    expect(screen.getByText('already in use')).toBeInTheDocument();
    expect(screen.getByLabelText('Email')).toHaveAttribute('aria-invalid', 'true');
  });
});
