import { describe, expect, it } from 'vitest';
import { formatRelativeDate } from './format';
import { validateAuthForm, validateQuestion } from './validation';

describe('auth form validation', () => {
  it('requires email and password on the login form', () => {
    const errors = validateAuthForm('login', { name: '', email: '', password: '' });

    expect(Object.keys(errors).sort()).toEqual(['email', 'password']);
    expect(errors.name).toBeUndefined();
  });

  it('rejects a malformed email and a short password', () => {
    const errors = validateAuthForm('login', { name: '', email: 'ada@', password: 'short' });

    expect(errors.email).toMatch(/valid email/i);
    expect(errors.password).toMatch(/between 8 and 72/i);
  });

  it('requires a name only when registering, and accepts valid input', () => {
    expect(validateAuthForm('register', { name: '', email: 'a@b.co', password: 'password12' }))
      .toHaveProperty('name');
    expect(
      validateAuthForm('register', { name: 'Ada', email: 'a@b.co', password: 'password12' }),
    ).toEqual({});
  });

  it('rejects a password above the 72 character backend limit', () => {
    const errors = validateAuthForm('login', {
      name: '',
      email: 'a@b.co',
      password: 'x'.repeat(73),
    });

    expect(errors.password).toMatch(/between 8 and 72/i);
  });
});

describe('question validation', () => {
  it('rejects blank questions and questions over 1000 characters', () => {
    expect(validateQuestion('   ')).toMatch(/type a question/i);
    expect(validateQuestion('a'.repeat(1001))).toMatch(/1000/);
    expect(validateQuestion('What is this about?')).toBeNull();
  });
});

describe('relative dates', () => {
  it('describes recent timestamps in words', () => {
    const now = new Date('2024-05-01T12:00:00Z');

    expect(formatRelativeDate('2024-05-01T11:59:40Z', now)).toBe('just now');
    expect(formatRelativeDate('2024-05-01T11:45:00Z', now)).toBe('15 min ago');
    expect(formatRelativeDate('2024-05-01T09:00:00Z', now)).toBe('3 h ago');
    expect(formatRelativeDate('2024-04-29T12:00:00Z', now)).toBe('2 d ago');
  });
});
