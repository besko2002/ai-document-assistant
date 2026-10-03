import { useEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { useParams } from 'react-router-dom';
import { api } from '../api/api';
import type { ChatMessage, DocumentSummary } from '../api/types';
import { Banner } from '../components/Banner';
import { SourceList } from '../components/SourceList';
import { formatBytes, pluralise } from '../lib/format';
import { friendlyError } from '../lib/messages';
import { QUESTION_MAX, validateQuestion } from '../lib/validation';

/**
 * Chat for one document. The route mounts it with `key={id}` so switching
 * documents remounts it with fresh state instead of resetting it in an effect.
 */
export function ChatPanel(): JSX.Element {
  const { id = '' } = useParams<{ id: string }>();

  const [document, setDocument] = useState<DocumentSummary | null>(null);
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [loading, setLoading] = useState(true);
  const [question, setQuestion] = useState('');
  const [pendingQuestion, setPendingQuestion] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);

  const scrollRef = useRef<HTMLDivElement>(null);
  const thinking = pendingQuestion !== null;

  useEffect(() => {
    const controller = new AbortController();
    let cancelled = false;

    const ignoreAbort = (cause: unknown): boolean =>
      cancelled || (cause instanceof Error && cause.name === 'AbortError');

    api
      .getDocument(id, controller.signal)
      .then((found) => {
        if (!cancelled) {
          setDocument(found);
        }
      })
      .catch((cause: unknown) => {
        if (!ignoreAbort(cause)) {
          setError(friendlyError(cause));
        }
      });

    api
      .listMessages(id, controller.signal)
      .then((history) => {
        if (!cancelled) {
          setMessages(history);
          setLoading(false);
        }
      })
      .catch((cause: unknown) => {
        if (!ignoreAbort(cause)) {
          setError(friendlyError(cause));
          setLoading(false);
        }
      });

    return () => {
      cancelled = true;
      controller.abort();
    };
  }, [id]);

  // Keep the newest exchange in view.
  useEffect(() => {
    const element = scrollRef.current;
    if (element !== null) {
      element.scrollTop = element.scrollHeight;
    }
  }, [messages.length, thinking]);

  async function send(): Promise<void> {
    if (thinking) {
      return;
    }
    const problem = validateQuestion(question);
    if (problem !== null) {
      setError(problem);
      return;
    }

    const asked = question.trim();
    setError(null);
    setQuestion('');
    setPendingQuestion(asked);
    try {
      const answered = await api.ask(id, asked);
      setMessages((previous) => [...previous, answered]);
    } catch (cause) {
      setError(friendlyError(cause));
      setQuestion(asked);
    } finally {
      setPendingQuestion(null);
    }
  }

  function handleKeyDown(event: KeyboardEvent<HTMLTextAreaElement>): void {
    if (event.key === 'Enter' && !event.shiftKey) {
      event.preventDefault();
      void send();
    }
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>): void {
    event.preventDefault();
    void send();
  }

  const subtitle =
    document === null
      ? 'Loading document…'
      : `${pluralise(document.pageCount, 'page')} · ${pluralise(document.chunkCount, 'chunk')} · ${formatBytes(document.sizeBytes)}`;

  return (
    <section className="chat" aria-label="Chat with your document">
      <header className="chat__header">
        <h1 className="chat__title">{document?.filename ?? 'Document'}</h1>
        <p className="chat__subtitle">{subtitle}</p>
      </header>

      <div className="chat__scroll" ref={scrollRef}>
        {loading ? (
          <p className="chat__loading" role="status">
            Loading conversation…
          </p>
        ) : messages.length === 0 && !thinking ? (
          <div className="empty empty--chat">
            <p className="empty__title">No questions yet</p>
            <p className="empty__text">
              Ask something like “What is this document about?” or “Summarise the conclusions.”
            </p>
          </div>
        ) : (
          <ol className="messages">
            {messages.map((message) => (
              <li key={message.id} className="exchange">
                <p className="bubble bubble--question">{message.question}</p>
                <div className="bubble bubble--answer">
                  <p className="answer-text">{message.answer}</p>
                  <SourceList sources={message.sources} />
                </div>
              </li>
            ))}
            {pendingQuestion !== null && (
              <li className="exchange">
                <p className="bubble bubble--question">{pendingQuestion}</p>
              </li>
            )}
          </ol>
        )}

        <div className="chat__thinking" role="status" aria-live="polite">
          {thinking && (
            <p className="thinking">
              <span className="thinking__dots" aria-hidden="true">
                <span />
                <span />
                <span />
              </span>
              Thinking…
            </p>
          )}
        </div>
      </div>

      <div className="chat__alerts">
        <Banner
          tone="error"
          message={error}
          onDismiss={() => {
            setError(null);
          }}
        />
      </div>

      <form className="composer" onSubmit={handleSubmit}>
        <label className="sr-only" htmlFor="question-input">
          Your question
        </label>
        <textarea
          id="question-input"
          className="composer__input"
          rows={2}
          maxLength={QUESTION_MAX}
          placeholder="Ask a question about this document…"
          value={question}
          disabled={thinking}
          onChange={(event) => {
            setQuestion(event.target.value);
          }}
          onKeyDown={handleKeyDown}
        />
        <div className="composer__actions">
          <p className="composer__hint">Enter to send · Shift+Enter for a new line</p>
          <button
            type="submit"
            className="button button--primary"
            disabled={thinking || question.trim().length === 0}
          >
            {thinking ? 'Sending…' : 'Send'}
          </button>
        </div>
      </form>
    </section>
  );
}
