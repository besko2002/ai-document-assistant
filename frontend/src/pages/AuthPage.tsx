import { useId, useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError } from '../api/ApiError';
import { useAuth } from '../auth/useAuth';
import { Banner } from '../components/Banner';
import { friendlyError } from '../lib/messages';
import { PASSWORD_MAX, PASSWORD_MIN, validateAuthForm, type FieldErrors } from '../lib/validation';

type Mode = 'login' | 'register';

const COPY: Record<Mode, { title: string; blurb: string; submit: string; busy: string }> = {
  login: {
    title: 'Sign in',
    blurb: 'Ask questions about your PDFs and get answers that cite their pages.',
    submit: 'Sign in',
    busy: 'Signing in…',
  },
  register: {
    title: 'Create your account',
    blurb: 'Upload a PDF, then chat with it. Answers always point back to the source pages.',
    submit: 'Create account',
    busy: 'Creating account…',
  },
};

export function AuthPage({ mode }: { mode: Mode }): JSX.Element {
  const copy = COPY[mode];
  const { login, register, notice, clearNotice } = useAuth();
  const navigate = useNavigate();
  const fieldId = useId();

  const [name, setName] = useState('');
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [errors, setErrors] = useState<FieldErrors>({});
  const [formError, setFormError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>): Promise<void> {
    event.preventDefault();
    setFormError(null);

    const clientErrors = validateAuthForm(mode, { name, email, password });
    setErrors(clientErrors);
    if (Object.keys(clientErrors).length > 0) {
      return;
    }

    setSubmitting(true);
    try {
      if (mode === 'register') {
        await register({ name: name.trim(), email: email.trim(), password });
      } else {
        await login({ email: email.trim(), password });
      }
      navigate('/', { replace: true });
    } catch (error) {
      if (error instanceof ApiError && Object.keys(error.fieldErrors).length > 0) {
        setErrors(error.fieldErrors);
      }
      setFormError(friendlyError(error));
    } finally {
      setSubmitting(false);
    }
  }

  const describedBy = (field: string): string | undefined =>
    errors[field] === undefined ? undefined : `${fieldId}-${field}-error`;

  return (
    <main className="auth-page">
      <section className="auth-card" aria-labelledby={`${fieldId}-title`}>
        <p className="brand brand--auth">
          <span className="brand__mark" aria-hidden="true">
            AI
          </span>
          AI Document Assistant
        </p>
        <h1 id={`${fieldId}-title`}>{copy.title}</h1>
        <p className="auth-card__blurb">{copy.blurb}</p>

        {notice !== null && <Banner tone="info" message={notice} onDismiss={clearNotice} />}
        <Banner tone="error" message={formError} />

        <form
          className="form"
          noValidate
          onSubmit={(event) => {
            void handleSubmit(event);
          }}
        >
          {mode === 'register' && (
            <div className="field">
              <label htmlFor={`${fieldId}-name`}>Name</label>
              <input
                id={`${fieldId}-name`}
                name="name"
                type="text"
                autoComplete="name"
                value={name}
                onChange={(event) => {
                  setName(event.target.value);
                }}
                aria-invalid={errors.name !== undefined}
                aria-describedby={describedBy('name')}
              />
              {errors.name !== undefined && (
                <p className="field__error" id={`${fieldId}-name-error`}>
                  {errors.name}
                </p>
              )}
            </div>
          )}

          <div className="field">
            <label htmlFor={`${fieldId}-email`}>Email</label>
            <input
              id={`${fieldId}-email`}
              name="email"
              type="email"
              autoComplete="email"
              value={email}
              onChange={(event) => {
                setEmail(event.target.value);
              }}
              aria-invalid={errors.email !== undefined}
              aria-describedby={describedBy('email')}
            />
            {errors.email !== undefined && (
              <p className="field__error" id={`${fieldId}-email-error`}>
                {errors.email}
              </p>
            )}
          </div>

          <div className="field">
            <label htmlFor={`${fieldId}-password`}>Password</label>
            <input
              id={`${fieldId}-password`}
              name="password"
              type="password"
              autoComplete={mode === 'register' ? 'new-password' : 'current-password'}
              value={password}
              onChange={(event) => {
                setPassword(event.target.value);
              }}
              aria-invalid={errors.password !== undefined}
              aria-describedby={describedBy('password')}
            />
            {errors.password !== undefined && (
              <p className="field__error" id={`${fieldId}-password-error`}>
                {errors.password}
              </p>
            )}
            {mode === 'register' && errors.password === undefined && (
              <p className="field__hint">
                {PASSWORD_MIN}–{PASSWORD_MAX} characters.
              </p>
            )}
          </div>

          <button type="submit" className="button button--primary" disabled={submitting}>
            {submitting ? copy.busy : copy.submit}
          </button>
        </form>

        <p className="auth-card__switch">
          {mode === 'login' ? (
            <>
              New here? <Link to="/register">Create an account</Link>
            </>
          ) : (
            <>
              Already have an account? <Link to="/login">Sign in</Link>
            </>
          )}
        </p>
      </section>
    </main>
  );
}
