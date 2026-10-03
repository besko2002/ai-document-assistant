/** Shapes returned by the Spring Boot backend (see PLAN.md, phases 2-3). */

/** Error envelope used by every non-2xx response. */
export interface ApiErrorBody {
  timestamp: string;
  status: number;
  error: string;
  message: string;
  path: string;
  fieldErrors?: Record<string, string>;
}

export interface User {
  id: string;
  name: string;
  email: string;
}

export interface AuthResponse {
  accessToken: string;
  tokenType: string;
  expiresIn: number;
  user: User;
}

export interface RegisterRequest {
  name: string;
  email: string;
  password: string;
}

export interface LoginRequest {
  email: string;
  password: string;
}

export interface DocumentSummary {
  id: string;
  filename: string;
  sizeBytes: number;
  pageCount: number;
  chunkCount: number;
  createdAt: string;
}

export interface Source {
  page: number;
  snippet: string;
  score: number;
}

export interface ChatMessage {
  id: string;
  question: string;
  answer: string;
  sources: Source[];
}
