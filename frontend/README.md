# AI Document Assistant — frontend

React 18 + TypeScript + Vite single-page app for the AI Document Assistant: sign in, upload a
PDF, and chat with it. Every answer is rendered as plain text and lists the source pages it was
grounded in.

## Requirements

- Node.js 20+ and npm
- The Spring Boot backend running on <http://localhost:8090> (phases 1–3 of `PLAN.md`)

## Run it

```bash
npm install
npm run dev     # http://localhost:5174
```

The dev server listens on port **5174** (`strictPort`, so it fails loudly instead of hopping to
another port) and proxies `/api` to `http://localhost:8090`. All request URLs in the code are
relative (`/api/...`), so no CORS configuration is needed on the backend.

Start the backend first, then open <http://localhost:5174>, register an account, and upload a PDF.
Uploading runs text extraction and embedding on the server, which takes a few seconds — the
sidebar shows upload progress and then a "Processing…" state.

## Scripts

| Script                   | What it does                                            |
| ------------------------ | ------------------------------------------------------- |
| `npm run dev`            | Dev server on 5174 with the `/api` proxy                |
| `npm run build`          | `tsc --noEmit` type-check, then a production bundle in `dist/` |
| `npm run preview`        | Serves the built bundle on 5174                         |
| `npm run test -- --run`  | Vitest suite once (CI mode)                             |
| `npm run test`           | Vitest in watch mode                                    |
| `npm run lint`           | ESLint over the whole project                           |

## How it is put together

```
src/
  api/        typed fetch client (ApiError parsing, bearer header, 401 handling)
  auth/       auth context/provider + route guards
  components/ banner, document list, upload zone, source cards
  lib/        validation, formatting, user-facing messages
  pages/      login/register, app shell, chat panel, empty state
  test/       vitest setup + shared fetch-mock helpers
  styles.css  the single stylesheet
```

- **Session**: the JWT lives in `localStorage` under `ada.accessToken`. On a cold start the app
  calls `GET /api/auth/me` to restore the user. Any `401` from an authenticated call clears the
  session and returns to `/login` with a "session expired" notice (the backend token lasts 1 h).
- **Errors**: every non-2xx response is parsed into an `ApiError` carrying `status`, `message` and
  `fieldErrors`; forms show field errors inline, and a `503` is rendered as
  "The AI service is busy, please try again in a moment".
- **Answers** are LLM plain text, rendered as text with `white-space: pre-wrap`. There is no
  `dangerouslySetInnerHTML` anywhere.
- **Upload** uses `XMLHttpRequest` (not `fetch`) because only XHR reports upload progress; PDFs
  are checked client-side for type and the 25 MB limit before any request is made.
- **Responsive**: under 800 px the sidebar becomes an off-canvas drawer toggled by the
  "☰ Documents" button in the header.

No environment variables are used, so there is no `.env.example`: the backend URL is set once in
the dev proxy in [vite.config.ts](vite.config.ts). For a production deploy, serve `dist/` behind a
reverse proxy that forwards `/api` to the backend.

## Tests

Vitest + Testing Library + jsdom, with `fetch` mocked by a small route table
([src/test/helpers.tsx](src/test/helpers.tsx)) — no test touches the network. The suite covers the
api client (bearer header, error-body parsing, 401 handling, 204, network failure), form
validation, login success storing the token and redirecting, protected-route redirects, the
document list and its delete confirmation, upload rejection of non-PDF and oversized files, and
the chat panel (history with sources, Enter vs Shift+Enter, "Thinking…", 503 banner).
