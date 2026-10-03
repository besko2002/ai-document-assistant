package com.example.docassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI provider configuration. API keys are read from the environment
 * ({@code GEMINI_API_KEY}, {@code GROQ_API_KEY}) and must never be logged or committed.
 */
@ConfigurationProperties(prefix = "ai")
public record AiProperties(Provider gemini, Provider groq) {

    public AiProperties {
        if (gemini == null) {
            gemini = new Provider(null, null, null);
        }
        if (groq == null) {
            groq = new Provider(null, null, null);
        }
    }

    public record Provider(String apiKey, String baseUrl, String chatModel) {

        public boolean hasApiKey() {
            return apiKey != null && !apiKey.isBlank();
        }
    }
}
