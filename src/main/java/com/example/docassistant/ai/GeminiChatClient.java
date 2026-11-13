package com.example.docassistant.ai;

import com.example.docassistant.common.BadRequestException;
import com.example.docassistant.common.ServiceUnavailableException;
import com.example.docassistant.config.AiProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;
import java.util.Optional;

/**
 * Google Gemini implementation of {@link ChatModelClient}.
 *
 * <p>Calls {@code POST {base-url}/models/{model}:generateContent?key=...} with
 * {@code {"contents":[{"parts":[{"text":"..."}]}]}} and reads the answer from
 * {@code candidates[0].content.parts[0].text}.
 *
 * <p>The API key is only ever placed in the request query string — it is never logged.
 */
@Slf4j
@Component
public class GeminiChatClient implements ChatModelClient {

    private static final int HTTP_TOO_MANY_REQUESTS = 429;

    private final RestClient restClient;
    private final AiProperties.Provider config;

    public GeminiChatClient(@Qualifier("geminiRestClient") RestClient restClient,
                            AiProperties properties) {
        this.restClient = restClient;
        this.config = properties.gemini();
    }

    @Override
    public String complete(String prompt) {
        if (prompt == null || prompt.isBlank()) {
            throw new BadRequestException("prompt must not be blank");
        }
        if (!config.hasApiKey()) {
            throw new ServiceUnavailableException("Gemini API key is not configured");
        }

        String model = config.chatModel();
        log.debug("Requesting completion from Gemini model {}", model);

        GeminiResponse response;
        try {
            response = restClient.post()
                    .uri(uri -> uri.path("/models/{model}:generateContent")
                            .queryParam("key", config.apiKey())
                            .build(model))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(GeminiRequest.of(prompt))
                    .retrieve()
                    .onStatus(status -> status.value() == HTTP_TOO_MANY_REQUESTS,
                            (request, clientResponse) -> {
                                // FallbackChatClient catches this and switches to Groq when it is configured.
                                log.warn("Gemini returned 429 for model {}", model);
                                throw new ServiceUnavailableException("AI provider is rate-limited");
                            })
                    .onStatus(status -> status.isError(),
                            (request, clientResponse) -> {
                                log.warn("Gemini call failed for model {} with status {}",
                                        model, clientResponse.getStatusCode().value());
                                throw new ServiceUnavailableException(
                                        "AI provider request failed with status "
                                                + clientResponse.getStatusCode().value());
                            })
                    .body(GeminiResponse.class);
        } catch (RestClientException ex) {
            throw new ServiceUnavailableException("AI provider is unreachable", ex);
        }

        return Optional.ofNullable(response)
                .map(GeminiResponse::candidates)
                .filter(candidates -> !candidates.isEmpty())
                .map(List::getFirst)
                .map(GeminiResponse.Candidate::content)
                .map(GeminiResponse.Content::parts)
                .filter(parts -> !parts.isEmpty())
                .map(List::getFirst)
                .map(GeminiResponse.Part::text)
                .orElseThrow(() -> new ServiceUnavailableException(
                        "AI provider returned an empty response"));
    }

    /** Request body: {@code {"contents":[{"parts":[{"text":"..."}]}]}}. */
    public record GeminiRequest(List<Content> contents) {

        public static GeminiRequest of(String prompt) {
            return new GeminiRequest(List.of(new Content(List.of(new Part(prompt)))));
        }

        public record Content(List<Part> parts) {
        }

        public record Part(String text) {
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GeminiResponse(List<Candidate> candidates) {

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Candidate(Content content) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Content(List<Part> parts) {
        }

        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Part(String text) {
        }
    }
}
