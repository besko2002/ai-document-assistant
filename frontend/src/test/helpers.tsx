import { render, type RenderResult } from '@testing-library/react';
import type { ReactElement } from 'react';
import { MemoryRouter } from 'react-router-dom';
import { vi, type Mock } from 'vitest';
import { App } from '../App';
import { AuthProvider } from '../auth/AuthProvider';
import type { ChatMessage, DocumentSummary } from '../api/types';

/** A canned HTTP reply for the fetch mock. */
export interface MockReply {
  status?: number;
  statusText?: string;
  /** Serialised as the JSON response body; omit for an empty body. */
  body?: unknown;
}

export interface RecordedCall {
  method: string;
  url: string;
  headers: Record<string, string>;
  body: unknown;
}

export type RouteHandler = MockReply | ((call: RecordedCall) => MockReply);

export interface FetchMock {
  spy: Mock<(input: RequestInfo | URL, init?: RequestInit) => Promise<Response>>;
  calls: RecordedCall[];
  callsTo: (key: string) => RecordedCall[];
}

const STATUS_TEXT: Record<number, string> = {
  200: 'OK',
  201: 'Created',
  204: 'No Content',
  400: 'Bad Request',
  401: 'Unauthorized',
  409: 'Conflict',
  413: 'Payload Too Large',
  500: 'Internal Server Error',
  503: 'Service Unavailable',
};

export function makeResponse(reply: MockReply): Response {
  const status = reply.status ?? 200;
  const text = reply.body === undefined ? '' : JSON.stringify(reply.body);
  const response = {
    ok: status >= 200 && status < 300,
    status,
    statusText: reply.statusText ?? STATUS_TEXT[status] ?? '',
    text: () => Promise.resolve(text),
  };
  return response as unknown as Response;
}

function parseBody(body: BodyInit | null | undefined): unknown {
  if (typeof body !== 'string') {
    return null;
  }
  try {
    return JSON.parse(body) as unknown;
  } catch {
    return body;
  }
}

/**
 * Replaces global fetch with a table-driven mock. Keys look like
 * `"GET /api/documents"`; an unexpected request fails the test loudly.
 */
export function installFetchMock(table: Record<string, RouteHandler>): FetchMock {
  const calls: RecordedCall[] = [];

  const spy = vi.fn((input: RequestInfo | URL, init?: RequestInit): Promise<Response> => {
    const url =
      typeof input === 'string' ? input : input instanceof URL ? input.toString() : input.url;
    const method = (init?.method ?? 'GET').toUpperCase();
    const headers = (init?.headers ?? {}) as Record<string, string>;
    const call: RecordedCall = { method, url, headers, body: parseBody(init?.body) };
    calls.push(call);

    const handler = table[`${method} ${url}`];
    if (handler === undefined) {
      return Promise.reject(new Error(`Unexpected request: ${method} ${url}`));
    }
    return Promise.resolve(makeResponse(typeof handler === 'function' ? handler(call) : handler));
  });

  vi.stubGlobal('fetch', spy);

  return {
    spy,
    calls,
    callsTo: (key: string) => calls.filter((call) => `${call.method} ${call.url}` === key),
  };
}

/** Renders the whole routed app at `path`, wrapped in the auth provider. */
export function renderApp(path: string): RenderResult {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <AuthProvider>
        <App />
      </AuthProvider>
    </MemoryRouter>,
  );
}

/** Renders one element inside a router (no auth provider needed). */
export function renderAt(path: string, element: ReactElement): RenderResult {
  return render(<MemoryRouter initialEntries={[path]}>{element}</MemoryRouter>);
}

export function makeDocument(overrides: Partial<DocumentSummary> = {}): DocumentSummary {
  return {
    id: 'doc-1',
    filename: 'handbook.pdf',
    sizeBytes: 204_800,
    pageCount: 12,
    chunkCount: 34,
    createdAt: new Date().toISOString(),
    ...overrides,
  };
}

export function makeMessage(overrides: Partial<ChatMessage> = {}): ChatMessage {
  return {
    id: 'msg-1',
    question: 'What is the refund policy?',
    answer: 'Refunds are granted within 30 days.',
    sources: [{ page: 2, snippet: 'Refunds within thirty days of purchase.', score: 0.81 }],
    ...overrides,
  };
}

export function makeFile(name: string, type: string, size: number): File {
  const file = new File(['x'], name, { type });
  Object.defineProperty(file, 'size', { value: size });
  return file;
}
