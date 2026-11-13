import { describe, expect, it, vi } from 'vitest';
import { installFetchMock, makeDocument } from '../test/helpers';
import { ApiError } from './ApiError';
import { api, setUnauthorizedHandler } from './api';
import { writeToken } from './token';

describe('api client', () => {
  it('adds the bearer header to authenticated calls', async () => {
    writeToken('jwt-123');
    const mock = installFetchMock({ 'GET /api/documents': { body: [makeDocument()] } });

    const documents = await api.listDocuments();

    expect(documents).toHaveLength(1);
    expect(mock.calls[0]?.headers.Authorization).toBe('Bearer jwt-123');
  });

  it('does not send a bearer header on login or register', async () => {
    writeToken('jwt-123');
    const mock = installFetchMock({
      'POST /api/auth/login': {
        body: {
          accessToken: 'new',
          tokenType: 'Bearer',
          expiresIn: 3600,
          user: { id: 'u1', name: 'Ada', email: 'ada@example.com' },
        },
      },
    });

    await api.login({ email: 'ada@example.com', password: 'password12' });

    expect(mock.calls[0]?.headers.Authorization).toBeUndefined();
    expect(mock.calls[0]?.body).toEqual({ email: 'ada@example.com', password: 'password12' });
  });

  it('parses the backend error envelope into an ApiError with fieldErrors', async () => {
    installFetchMock({
      'POST /api/auth/register': {
        status: 400,
        body: {
          timestamp: '2024-05-01T10:00:00Z',
          status: 400,
          error: 'Bad Request',
          message: 'Validation failed',
          path: '/api/auth/register',
          fieldErrors: { email: 'must be a well-formed email address', extra: 42 },
        },
      },
    });

    const error = await api
      .register({ name: 'Ada', email: 'nope', password: 'password12' })
      .catch((cause: unknown) => cause);

    expect(error).toBeInstanceOf(ApiError);
    const apiError = error as ApiError;
    expect(apiError.status).toBe(400);
    expect(apiError.message).toBe('Validation failed');
    expect(apiError.fieldErrors).toEqual({ email: 'must be a well-formed email address' });
  });

  it('falls back to a status-based message when the body is not the error envelope', async () => {
    installFetchMock({ 'GET /api/documents': { status: 500, body: 'boom' } });

    const error = await api.listDocuments().catch((cause: unknown) => cause);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).message).toBe('Request failed (500 Internal Server Error).');
  });

  it('keeps the 503 message from the AI-busy response', async () => {
    installFetchMock({
      'POST /api/documents/doc-1/ask': {
        status: 503,
        body: { status: 503, message: 'AI provider is busy' },
      },
    });

    const error = await api.ask('doc-1', 'Why?').catch((cause: unknown) => cause);

    expect((error as ApiError).status).toBe(503);
    expect((error as ApiError).message).toBe('AI provider is busy');
  });

  it('fires the unauthorized handler when an authenticated call returns 401', async () => {
    writeToken('expired');
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    installFetchMock({
      'GET /api/documents': { status: 401, body: { status: 401, message: 'Token expired' } },
    });

    const error = await api.listDocuments().catch((cause: unknown) => cause);

    expect((error as ApiError).status).toBe(401);
    expect(onUnauthorized).toHaveBeenCalledTimes(1);
  });

  it('does not fire the unauthorized handler for a failed login', async () => {
    const onUnauthorized = vi.fn();
    setUnauthorizedHandler(onUnauthorized);
    installFetchMock({
      'POST /api/auth/login': { status: 401, body: { status: 401, message: 'Bad credentials' } },
    });

    const error = await api
      .login({ email: 'ada@example.com', password: 'wrongpassword' })
      .catch((cause: unknown) => cause);

    expect((error as ApiError).message).toBe('Bad credentials');
    expect(onUnauthorized).not.toHaveBeenCalled();
  });

  it('resolves a 204 delete without a body', async () => {
    writeToken('jwt-123');
    const mock = installFetchMock({ 'DELETE /api/documents/doc-1': { status: 204 } });

    await expect(api.deleteDocument('doc-1')).resolves.toBeUndefined();
    expect(mock.calls[0]?.method).toBe('DELETE');
  });

  it('reports a transport failure as a network ApiError', async () => {
    vi.stubGlobal(
      'fetch',
      vi.fn(() => Promise.reject(new TypeError('Failed to fetch'))),
    );

    const error = await api.me().catch((cause: unknown) => cause);

    expect(error).toBeInstanceOf(ApiError);
    expect((error as ApiError).isNetworkError).toBe(true);
    expect((error as ApiError).status).toBe(0);
  });

  it('posts the question as JSON to the ask endpoint', async () => {
    writeToken('jwt-123');
    const mock = installFetchMock({
      'POST /api/documents/doc%201/ask': {
        body: { id: 'm1', question: 'Why?', answer: 'Because.', sources: [] },
      },
    });

    const answer = await api.ask('doc 1', 'Why?');

    expect(answer.answer).toBe('Because.');
    expect(mock.calls[0]?.body).toEqual({ question: 'Why?' });
    expect(mock.calls[0]?.headers['Content-Type']).toBe('application/json');
  });
});
