import { fireEvent, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { installFetchMock, makeFile } from '../test/helpers';
import { UploadZone } from './UploadZone';

describe('upload zone', () => {
  // A dropped file bypasses the input's `accept` filter, so this is the path
  // where a non-PDF can actually reach us.
  it('rejects a dropped file that is not a PDF without calling the server', async () => {
    const mock = installFetchMock({});
    const onUploaded = vi.fn();
    render(<UploadZone onUploaded={onUploaded} />);

    fireEvent.drop(screen.getByRole('group', { name: 'Upload a PDF' }), {
      dataTransfer: { files: [makeFile('notes.txt', 'text/plain', 1024)] },
    });

    expect(await screen.findByText('Only PDF files are supported.')).toBeInTheDocument();
    expect(mock.spy).not.toHaveBeenCalled();
    expect(onUploaded).not.toHaveBeenCalled();
  });

  it('rejects a PDF larger than the 25 MB limit', async () => {
    const user = userEvent.setup();
    const mock = installFetchMock({});
    render(<UploadZone onUploaded={vi.fn()} />);

    await user.upload(
      screen.getByLabelText('PDF file'),
      makeFile('huge.pdf', 'application/pdf', 26 * 1024 * 1024),
    );

    expect(await screen.findByText(/the limit is 25 MB/)).toBeInTheDocument();
    expect(mock.spy).not.toHaveBeenCalled();
  });

  it('offers an accessible upload button and drop hint', () => {
    render(<UploadZone onUploaded={vi.fn()} />);

    expect(screen.getByRole('button', { name: 'Upload PDF' })).toBeEnabled();
    expect(screen.getByText(/drop a PDF here/i)).toBeInTheDocument();
    expect(screen.getByLabelText('PDF file')).toHaveAttribute('accept', 'application/pdf,.pdf');
  });
});
