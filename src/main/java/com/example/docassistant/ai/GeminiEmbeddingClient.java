package com.example.docassistant.ai;

import com.example.docassistant.common.ServiceUnavailableException;
import com.example.docassistant.config.AiProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.List;

/**
 * Gemini embeddings through {@code models/{model}:batchEmbedContents}.
 * Passages go out in batches; a 429 is retried a few times with a growing pause before giving up.
 */
@Slf4j
@Component
public class GeminiEmbeddingClient implements EmbeddingClient {

    private static final int BATCH_SIZE = 100;
    private static final int MAX_ATTEMPTS = 4;

    private final RestClient restClient;
    private final AiProperties.Provider config;
    private final String model;
    private final int dimensions;
    private final long retryDelayMs;

    public GeminiEmbeddingClient(@Qualifier("geminiRestClient") RestClient restClient, AiProperties properties,
                                 @Value("${ai.gemini.embedding-model:gemini-embedding-001}") String model,
                                 @Value("${app.rag.embedding-dimensions:768}") int dimensions,
                                 @Value("${app.rag.retry-delay-ms:2000}") long retryDelayMs) {
        this.restClient = restClient;
        this.config = properties.gemini();
        this.model = model;
        this.dimensions = dimensions;
        this.retryDelayMs = retryDelayMs;
    }

    @Override
    public List<float[]> embedDocuments(List<String> texts) {
        List<float[]> all = new ArrayList<>(texts.size());
        for (int from = 0; from < texts.size(); from += BATCH_SIZE) {
            all.addAll(embedBatch(texts.subList(from, Math.min(from + BATCH_SIZE, texts.size())), "RETRIEVAL_DOCUMENT"));
        }
        return all;
    }

    @Override
    public float[] embedQuery(String text) {
        return embedBatch(List.of(text), "RETRIEVAL_QUERY").getFirst();
    }

    private List<float[]> embedBatch(List<String> texts, String taskType) {
        if (!config.hasApiKey()) {
            throw new ServiceUnavailableException("Gemini API key is not configured");
        }
        Request body = new Request(texts.stream().map(t -> new Item("models/" + model,
                new Content(List.of(new Part(t))), taskType, dimensions)).toList());

        for (int attempt = 1; ; attempt++) {
            try {
                Response response = restClient.post()
                        .uri(uri -> uri.path("/models/{model}:batchEmbedContents")
                                .queryParam("key", config.apiKey()).build(model))
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body)
                        .retrieve()
                        .onStatus(s -> s.value() == 429, (req, res) -> {
                            throw new RateLimited();
                        })
                        .onStatus(s -> s.isError(), (req, res) -> {
                            throw new ServiceUnavailableException(
                                    "Embedding request failed with status " + res.getStatusCode().value());
                        })
                        .body(Response.class);
                return toVectors(response, texts.size());
            } catch (RateLimited limited) {
                if (attempt >= MAX_ATTEMPTS) {
                    throw new ServiceUnavailableException("AI provider is rate-limited, try again in a minute");
                }
                log.warn("Gemini embeddings rate-limited (attempt {}/{}), waiting", attempt, MAX_ATTEMPTS);
                pause(retryDelayMs * attempt);
            } catch (RestClientException e) {
                throw new ServiceUnavailableException("AI provider is unreachable", e);
            }
        }
    }

    private List<float[]> toVectors(Response response, int expected) {
        if (response == null || response.embeddings() == null || response.embeddings().size() != expected) {
            throw new ServiceUnavailableException("AI provider returned an unexpected embedding response");
        }
        List<float[]> vectors = new ArrayList<>(expected);
        for (Embedding e : response.embeddings()) {
            if (e.values() == null || e.values().size() != dimensions) {
                throw new ServiceUnavailableException("AI provider returned a vector of the wrong size");
            }
            float[] v = new float[dimensions];
            for (int i = 0; i < dimensions; i++) {
                v[i] = e.values().get(i);
            }
            vectors.add(v);
        }
        return vectors;
    }

    private static void pause(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
            throw new ServiceUnavailableException("Interrupted while waiting for the AI provider");
        }
    }

    private static final class RateLimited extends RuntimeException {
        RateLimited() {
            super(null, null, false, false);
        }
    }

    record Request(List<Item> requests) {
    }

    record Item(String model, Content content, String taskType, int outputDimensionality) {
    }

    record Content(List<Part> parts) {
    }

    record Part(String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(List<Embedding> embeddings) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Embedding(List<Float> values) {
    }
}
