package com.example.docassistant.ai;

import com.example.docassistant.common.ServiceUnavailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;

/** What the application talks to: Gemini first, Groq when Gemini is unavailable and Groq is configured. */
@Slf4j
@Primary
@Component
public class FallbackChatClient implements ChatModelClient {

    private static final int GEMINI_ATTEMPTS = 3;

    private final GeminiChatClient primary;
    private final GroqChatClient fallback;
    private final long retryDelayMs;

    public FallbackChatClient(GeminiChatClient primary, GroqChatClient fallback,
                              @Value("${app.rag.retry-delay-ms:2000}") long retryDelayMs) {
        this.primary = primary;
        this.fallback = fallback;
        this.retryDelayMs = retryDelayMs;
    }

    @Override
    public String complete(String prompt) {
        ServiceUnavailableException last = null;
        // Gemini's free tier often answers 503/429 for a moment: a short retry hides that from the user.
        for (int attempt = 1; attempt <= GEMINI_ATTEMPTS; attempt++) {
            try {
                return primary.complete(prompt);
            } catch (ServiceUnavailableException e) {
                last = e;
                if (attempt < GEMINI_ATTEMPTS) {
                    log.warn("Gemini unavailable ({}), retry {}/{}", e.getMessage(), attempt, GEMINI_ATTEMPTS - 1);
                    pause(retryDelayMs * attempt);
                }
            }
        }
        if (!fallback.isConfigured()) {
            throw last;
        }
        log.warn("Gemini still unavailable ({}), using the Groq fallback", last.getMessage());
        return fallback.complete(prompt);
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException("Interrupted while waiting for the AI provider");
        }
    }
}
