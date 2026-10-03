# AI Document Assistant — Plan

Upload a PDF, ask questions about it in a chat. Answers are grounded in the document
(Retrieval-Augmented Generation) and cite the pages they came from.

## Stack
- Backend: Java 21, Spring Boot 3.5, PostgreSQL 16 + pgvector, Flyway, Spring Security (JWT),
  Apache PDFBox (text extraction), springdoc, Testcontainers.
- AI: Google Gemini free API (embeddings + chat) via an OpenAI-style client, with an
  automatic fallback to Groq on rate-limit (429). Keys from env vars, never committed.
- Frontend: React 18 + TypeScript + Vite.
- Docker Compose, GitHub Actions.

## How it works (RAG)
1. Upload PDF -> extract text -> split into chunks (with page numbers).
2. Each chunk -> embedding (Gemini) -> stored as a vector in pgvector.
3. Question -> embed it -> find the nearest chunks -> send them + the question to Gemini ->
   answer with the source pages.

## Phases
1. Setup: Spring Boot, docker-compose (Postgres+pgvector), Gemini client with Groq fallback,
   health check, baseline Flyway.
2. Auth + upload: register/login (JWT), upload PDF, extract text, chunk + store (per user).
3. Embeddings + RAG: embed chunks, vector search, chat endpoint that answers with citations;
   list/delete documents; chat history.
4. React frontend: login, document list + upload, chat UI showing sources.
5. Polish: Swagger, README + screenshots, Docker build of everything, CI, push to GitHub.

## Ports / config (avoid ones already used)
Backend 8090, Postgres 5436, frontend 5174.
Env: GEMINI_API_KEY, GROQ_API_KEY (optional fallback), JWT_SECRET, DB_*.
