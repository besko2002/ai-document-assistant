interface BannerProps {
  tone: 'error' | 'info';
  message: string | null;
  onDismiss?: () => void;
}

/**
 * Friendly, polite-announced message strip. Always rendered (empty when there
 * is nothing to say) so screen readers pick up later updates.
 */
export function Banner({ tone, message, onDismiss }: BannerProps): JSX.Element {
  return (
    <div className="banner-slot" role="status" aria-live="polite">
      {message !== null && (
        <div className={`banner banner--${tone}`}>
          <span className="banner__icon" aria-hidden="true">
            {tone === 'error' ? '!' : 'i'}
          </span>
          <p className="banner__text">{message}</p>
          {onDismiss && (
            <button type="button" className="banner__close" onClick={onDismiss} aria-label="Dismiss message">
              ×
            </button>
          )}
        </div>
      )}
    </div>
  );
}
