/** Client-side mirrors of the backend bean-validation rules. */

export const PASSWORD_MIN = 8;
export const PASSWORD_MAX = 72;
export const NAME_MAX = 120;
export const QUESTION_MAX = 1000;

export type FieldErrors = Record<string, string>;

const EMAIL_PATTERN = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function validateName(name: string): string | null {
  if (name.trim().length === 0) {
    return 'Please enter your name.';
  }
  if (name.trim().length > NAME_MAX) {
    return `Name must be at most ${String(NAME_MAX)} characters.`;
  }
  return null;
}

export function validateEmail(email: string): string | null {
  if (email.trim().length === 0) {
    return 'Please enter your email address.';
  }
  if (!EMAIL_PATTERN.test(email.trim())) {
    return 'Please enter a valid email address.';
  }
  return null;
}

export function validatePassword(password: string): string | null {
  if (password.length === 0) {
    return 'Please enter your password.';
  }
  if (password.length < PASSWORD_MIN || password.length > PASSWORD_MAX) {
    return `Password must be between ${String(PASSWORD_MIN)} and ${String(PASSWORD_MAX)} characters.`;
  }
  return null;
}

export interface AuthFormValues {
  name: string;
  email: string;
  password: string;
}

/** Validates the login/register form; an empty object means "valid". */
export function validateAuthForm(mode: 'login' | 'register', values: AuthFormValues): FieldErrors {
  const errors: FieldErrors = {};

  if (mode === 'register') {
    const nameError = validateName(values.name);
    if (nameError !== null) {
      errors.name = nameError;
    }
  }

  const emailError = validateEmail(values.email);
  if (emailError !== null) {
    errors.email = emailError;
  }

  const passwordError = validatePassword(values.password);
  if (passwordError !== null) {
    errors.password = passwordError;
  }

  return errors;
}

export function validateQuestion(question: string): string | null {
  if (question.trim().length === 0) {
    return 'Please type a question first.';
  }
  if (question.trim().length > QUESTION_MAX) {
    return `Questions are limited to ${String(QUESTION_MAX)} characters.`;
  }
  return null;
}
