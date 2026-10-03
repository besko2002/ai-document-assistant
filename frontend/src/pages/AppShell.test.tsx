import { screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it } from 'vitest';
import { writeToken } from '../api/token';
import { installFetchMock, makeDocument, renderApp } from '../test/helpers';

const ME = { id: 'u1', name: 'Ada Lovelace', email: 'ada@example.com' };

const DOCS = [
  makeDocument({
    id: 'doc-1',
    filename: 'handbook.pdf',
    pageCount: 12,
    createdAt: new Date(Date.now() - 2 * 60 * 60 * 1000).toISOString(),
  }),
  makeDocument({
    id: 'doc-2',
    filename: 'invoice.pdf',
    pageCount: 1,
    createdAt: new Date(Date.now() - 10 * 60 * 1000).toISOString(),
  }),
];

beforeEach(() => {
  writeToken('jwt-abc');
});

describe('document sidebar', () => {
  it('renders every document with its page count and relative date', async () => {
    installFetchMock({
      'GET /api/auth/me': { body: ME },
      'GET /api/documents': { body: DOCS },
    });

    renderApp('/');

    expect(await screen.findByText('handbook.pdf')).toBeInTheDocument();
    expect(screen.getByText('invoice.pdf')).toBeInTheDocument();
    expect(screen.getByText(/12 pages · 2 h ago/)).toBeInTheDocument();
    expect(screen.getByText(/1 page · 10 min ago/)).toBeInTheDocument();
    expect(screen.getByText(/pick a document/i)).toBeInTheDocument();
  });

  it('shows an empty state when there are no documents', async () => {
    installFetchMock({
      'GET /api/auth/me': { body: ME },
      'GET /api/documents': { body: [] },
    });

    renderApp('/');

    expect(await screen.findByText('No documents yet')).toBeInTheDocument();
  });

  it('asks for confirmation before deleting and does nothing when cancelled', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock({
      'GET /api/auth/me': { body: ME },
      'GET /api/documents': { body: DOCS },
      'DELETE /api/documents/doc-1': { status: 204 },
    });

    renderApp('/');
    await screen.findByText('handbook.pdf');

    await user.click(screen.getByRole('button', { name: 'Delete handbook.pdf' }));
    expect(screen.getByText('Delete this document?')).toBeInTheDocument();

    await user.click(screen.getByRole('button', { name: 'Cancel' }));

    expect(mock.callsTo('DELETE /api/documents/doc-1')).toHaveLength(0);
    expect(screen.getByText('handbook.pdf')).toBeInTheDocument();
  });

  it('deletes the document once the confirm button is pressed', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock({
      'GET /api/auth/me': { body: ME },
      'GET /api/documents': { body: DOCS },
      'DELETE /api/documents/doc-1': { status: 204 },
    });

    renderApp('/');
    await screen.findByText('handbook.pdf');

    await user.click(screen.getByRole('button', { name: 'Delete handbook.pdf' }));
    const confirmRow = screen.getByText('Delete this document?').parentElement;
    expect(confirmRow).not.toBeNull();
    await user.click(within(confirmRow as HTMLElement).getByRole('button', { name: 'Delete' }));

    await waitFor(() => {
      expect(mock.callsTo('DELETE /api/documents/doc-1')).toHaveLength(1);
    });
    await waitFor(() => {
      expect(screen.queryByText('handbook.pdf')).not.toBeInTheDocument();
    });
    expect(screen.getByText('invoice.pdf')).toBeInTheDocument();
  });

  it('shows a friendly banner when the document list cannot be loaded', async () => {
    installFetchMock({
      'GET /api/auth/me': { body: ME },
      'GET /api/documents': {
        status: 503,
        body: { status: 503, message: 'provider busy' },
      },
    });

    renderApp('/');

    expect(
      await screen.findByText('The AI service is busy, please try again in a moment'),
    ).toBeInTheDocument();
  });
});
