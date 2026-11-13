import { MAX_UPLOAD_BYTES } from '../api/api';

/** Returns an error message when the picked file is not an acceptable PDF. */
export function validatePdfFile(file: File): string | null {
  const isPdf =
    file.type === 'application/pdf' || file.name.toLowerCase().endsWith('.pdf');
  if (!isPdf) {
    return 'Only PDF files are supported.';
  }
  if (file.size === 0) {
    return 'That file is empty.';
  }
  if (file.size > MAX_UPLOAD_BYTES) {
    const megabytes = (file.size / (1024 * 1024)).toFixed(1);
    return `That file is ${megabytes} MB — the limit is 25 MB.`;
  }
  return null;
}
