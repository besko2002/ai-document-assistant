import { screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Route, Routes } from 'react-router-dom';
import { writeToken } from '../api/token';
import {
  installFetchMock,
  makeDocument,
  makeMessage,
  makeResponse,
  renderAt,
} from '../test/helpers';
import { ChatPanel } from './ChatPanel';

const LONG_SNIPPET =
  'Refunds are available within thirty days of purchase, provided the licence has not been activated on more than two machines.';

function renderChat(): void {
  renderAt(
    '/documents/doc-1',
    <Routes>
      <Route path="/documents/:id" element={<ChatPanel />} />
    </Routes>,
  );
}

beforeEach(() => {
  writeToken('jwt-abc');
});

describe('chat panel', () => {
  it('renders the stored history with its collapsible sources', async () => {
    installFetchMock({
      'GET /api/documents/doc-1': { body: makeDocument() },
      'GET /api/documents/doc-1/messages': {
        body: [makeMessage({ sources: [{ page: 2, snippet: LONG_SNIPPET, score: 0.8125 }] })],
      },
    });

    renderChat();

    expect(await screen.findByText('What is the refund policy?')).toBeInTheDocument();
    expect(screen.getByText('Refunds are granted within 30 days.')).toBeInTheDocument();
    expect(screen.getByText('Page 2')).toBeInTheDocument();
    expect(screen.getByText('(0.81)')).toBeInTheDocument();
    expect(screen.getByText(LONG_SNIPPET)).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'handbook.pdf' })).toBeInTheDocument();
  });

  it('shows an empty state when the document has no messages', async () => {
    installFetchMock({
      'GET /api/documents/doc-1': { body: makeDocument() },
      'GET /api/documents/doc-1/messages': { body: [] },
    });

    renderChat();

    expect(await screen.findByText('No questions yet')).toBeInTheDocument();
  });

  it('sends a question and shows the answer with its sources', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock({
      'GET /api/documents/doc-1': { body: makeDocument() },
      'GET /api/documents/doc-1/messages': { body: [] },
      'POST /api/documents/doc-1/ask': {
        body: {
          id: 'msg-9',
          question: 'What is the refund window?',
          answer: 'Thirty days.\nNo exceptions.',
          sources: [{ page: 7, snippet: LONG_SNIPPET, score: 0.42 }],
        },
      },
    });

    renderChat();
    await screen.findByText('No questions yet');

    await user.type(screen.getByLabelText('Your question'), 'What is the refund window?');
    await user.click(screen.getByRole('button', { name: 'Send' }));

    expect(await screen.findByText(/Thirty days\./)).toBeInTheDocument();
    expect(screen.getByText('Page 7')).toBeInTheDocument();
    expect(screen.getByText('(0.42)')).toBeInTheDocument();
    expect(mock.callsTo('POST /api/documents/doc-1/ask')[0]?.body).toEqual({
      question: 'What is the refund window?',
    });
  });

  it('shows the busy message when the AI provider answers 503', async () => {
    const user = userEvent.setup();
    installFetchMock({
      'GET /api/documents/doc-1': { body: makeDocument() },
      'GET /api/documents/doc-1/messages': { body: [] },
      'POST /api/documents/doc-1/ask': {
        status: 503,
        body: { status: 503, message: 'Gemini and Groq are rate limited' },
      },
    });

    renderChat();
    await screen.findByText('No questions yet');

    await user.type(screen.getByLabelText('Your question'), 'Summarise this');
    await user.click(screen.getByRole('button', { name: 'Send' }));

    expect(
      await screen.findByText('The AI service is busy, please try again in a moment'),
    ).toBeInTheDocument();
    expect(screen.getByLabelText('Your question')).toHaveValue('Summarise this');
  });

  it('sends on Enter but inserts a newline on Shift+Enter', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock({
      'GET /api/documents/doc-1': { body: makeDocument() },
      'GET /api/documents/doc-1/messages': { body: [] },
      'POST /api/documents/doc-1/ask': {
        body: { id: 'msg-2', question: 'line one\nline two', answer: 'Answered.', sources: [] },
      },
    });

    renderChat();
    await screen.findByText('No questions yet');

    const input = screen.getByLabelText('Your question');
    await user.type(input, 'line one{Shift>}{Enter}{/Shift}line two');

    expect(input).toHaveValue('line one\nline two');
    expect(mock.callsTo('POST /api/documents/doc-1/ask')).toHaveLength(0);

    await user.type(input, '{Enter}');

    await waitFor(() => {
      expect(mock.callsTo('POST /api/documents/doc-1/ask')).toHaveLength(1);
    });
    expect(await screen.findByText('Answered.')).toBeInTheDocument();
    expect(input).toHaveValue('');
  });

  it('announces the thinking state while an answer is pending', async () => {
    const user = userEvent.setup();
    let release: (() => void) = () => undefined;
    const pending = new Promise<void>((resolve) => {
      release = resolve;
    });

    vi.stubGlobal(
      'fetch',
      vi.fn((input: RequestInfo | URL): Promise<Response> => {
        const url = String(input);
        if (url === '/api/documents/doc-1') {
          return Promise.resolve(makeResponse({ body: makeDocument() }));
        }
        if (url.endsWith('/messages')) {
          return Promise.resolve(makeResponse({ body: [] }));
        }
        return pending.then(() =>
          makeResponse({ body: { id: 'm9', question: 'q', answer: 'Eventually.', sources: [] } }),
        );
      }),
    );

    renderChat();
    await screen.findByText('No questions yet');

    await user.type(screen.getByLabelText('Your question'), 'slow question{Enter}');

    expect(await screen.findByText('Thinking…')).toBeInTheDocument();
    expect(screen.getByLabelText('Your question')).toBeDisabled();
    expect(screen.getByRole('button', { name: 'Sending…' })).toBeDisabled();

    release();

    expect(await screen.findByText('Eventually.')).toBeInTheDocument();
    expect(screen.queryByText('Thinking…')).not.toBeInTheDocument();
  });
});
