import { ApiError } from '../api/ApiError';

export const SESSION_EXPIRED_NOTICE = 'Your session expired. Please sign in again.';

export const AI_BUSY_MESSAGE = 'The AI service is busy, please try again in a moment';

/** Turns any thrown value into a sentence worth showing in a banner. */
export function friendlyError(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.status === 503) {
      return AI_BUSY_MESSAGE;
    }
    if (error.status === 413) {
      return 'That file is too large for the server. Try a smaller PDF.';
    }
    return error.message;
  }
  if (error instanceof Error && error.message.trim().length > 0) {
    return error.message;
  }
  return 'Something went wrong. Please try again.';
}
