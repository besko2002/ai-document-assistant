import { ApiError, NETWORK_ERROR_MESSAGE, NETWORK_ERROR_STATUS, toApiError } from './ApiError';
import { readToken } from './token';
import type {
  AuthResponse,
  ChatMessage,
  DocumentSummary,
  LoginRequest,
  RegisterRequest,
  User,
} from './types';

/** Largest upload we accept client-side (the backend answers 413 above its own limit). */
export const MAX_UPLOAD_BYTES = 25 * 1024 * 1024;

type UnauthorizedHandler = () => void;

let unauthorizedHandler: UnauthorizedHandler | null = null;

/**
 * Registers the callback fired when an authenticated call comes back 401,
 * i.e. the one-hour token expired. The auth provider uses it to drop the
 * session and bounce the user to /login.
 */
export function setUnauthorizedHandler(handler: UnauthorizedHandler | null): void {
  unauthorizedHandler = handler;
}

interface RequestOptions {
  method?: 'GET' | 'POST' | 'DELETE';
  /** Serialised as a JSON request body. */
  json?: unknown;
  /** Set to false for /api/auth/register and /api/auth/login. */
  auth?: boolean;
  signal?: AbortSignal;
}

function parseJsonText(text: string): unknown {
  if (text.trim().length === 0) {
    return null;
  }
  try {
    return JSON.parse(text) as unknown;
  } catch {
    return null;
  }
}

async function readBody(response: Response): Promise<unknown> {
  try {
    return parseJsonText(await response.text());
  } catch {
    return null;
  }
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = 'GET', json, auth = true, signal } = options;

  const headers: Record<string, string> = { Accept: 'application/json' };
  if (auth) {
    const token = readToken();
    if (token !== null && token.length > 0) {
      headers.Authorization = `Bearer ${token}`;
    }
  }

  let body: string | undefined;
  if (json !== undefined) {
    body = JSON.stringify(json);
    headers['Content-Type'] = 'application/json';
  }

  let response: Response;
  try {
    response = await fetch(path, { method, headers, body, signal });
  } catch (cause) {
    if (cause instanceof Error && cause.name === 'AbortError') {
      throw cause;
    }
    throw new ApiError(NETWORK_ERROR_STATUS, NETWORK_ERROR_MESSAGE);
  }

  if (!response.ok) {
    const error = toApiError(response.status, response.statusText, await readBody(response));
    if (response.status === 401 && auth) {
      unauthorizedHandler?.();
    }
    throw error;
  }

  if (response.status === 204) {
    return undefined as T;
  }
  return (await readBody(response)) as T;
}

/**
 * Uploads a PDF. Uses XMLHttpRequest instead of fetch because only XHR
 * reports upload progress, which matters here: embedding takes seconds.
 */
function uploadDocument(
  file: File,
  onProgress?: (percent: number) => void,
): Promise<DocumentSummary> {
  return new Promise<DocumentSummary>((resolve, reject) => {
    const xhr = new XMLHttpRequest();
    xhr.open('POST', '/api/documents');
    xhr.setRequestHeader('Accept', 'application/json');
    const token = readToken();
    if (token !== null && token.length > 0) {
      xhr.setRequestHeader('Authorization', `Bearer ${token}`);
    }

    if (onProgress) {
      xhr.upload.onprogress = (event: ProgressEvent): void => {
        if (event.lengthComputable && event.total > 0) {
          onProgress(Math.min(100, Math.round((event.loaded / event.total) * 100)));
        }
      };
    }

    xhr.onerror = (): void => {
      reject(new ApiError(NETWORK_ERROR_STATUS, NETWORK_ERROR_MESSAGE));
    };

    xhr.onload = (): void => {
      const body = parseJsonText(xhr.responseText);
      if (xhr.status >= 200 && xhr.status < 300) {
        resolve(body as DocumentSummary);
        return;
      }
      const error = toApiError(xhr.status, xhr.statusText, body);
      if (xhr.status === 401) {
        unauthorizedHandler?.();
      }
      reject(error);
    };

    const form = new FormData();
    form.append('file', file);
    xhr.send(form);
  });
}

const id = (value: string): string => encodeURIComponent(value);

export const api = {
  register: (payload: RegisterRequest): Promise<AuthResponse> =>
    request<AuthResponse>('/api/auth/register', { method: 'POST', json: payload, auth: false }),

  login: (payload: LoginRequest): Promise<AuthResponse> =>
    request<AuthResponse>('/api/auth/login', { method: 'POST', json: payload, auth: false }),

  me: (signal?: AbortSignal): Promise<User> => request<User>('/api/auth/me', { signal }),

  listDocuments: (signal?: AbortSignal): Promise<DocumentSummary[]> =>
    request<DocumentSummary[]>('/api/documents', { signal }),

  getDocument: (documentId: string, signal?: AbortSignal): Promise<DocumentSummary> =>
    request<DocumentSummary>(`/api/documents/${id(documentId)}`, { signal }),

  deleteDocument: (documentId: string): Promise<void> =>
    request<void>(`/api/documents/${id(documentId)}`, { method: 'DELETE' }),

  listMessages: (documentId: string, signal?: AbortSignal): Promise<ChatMessage[]> =>
    request<ChatMessage[]>(`/api/documents/${id(documentId)}/messages`, { signal }),

  ask: (documentId: string, question: string): Promise<ChatMessage> =>
    request<ChatMessage>(`/api/documents/${id(documentId)}/ask`, {
      method: 'POST',
      json: { question },
    }),

  uploadDocument,
};

export type Api = typeof api;
