package com.example.docassistant.ai;

import com.example.docassistant.common.ServiceUnavailableException;
import com.example.docassistant.config.AiProperties;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.util.List;

/** Groq (OpenAI-compatible chat completions): the backup used when Gemini is rate-limited. */
@Slf4j
@Component
public class GroqChatClient implements ChatModelClient {

    private final RestClient restClient;
    private final AiProperties.Provider config;

    public GroqChatClient(@Qualifier("groqRestClient") RestClient restClient, AiProperties properties) {
        this.restClient = restClient;
        this.config = properties.groq();
    }

    public boolean isConfigured() {
        return config.hasApiKey();
    }

    @Override
    public String complete(String prompt) {
        if (!config.hasApiKey()) {
            throw new ServiceUnavailableException("Groq API key is not configured");
        }
        Response response;
        try {
            response = restClient.post()
                    .uri("/chat/completions")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + config.apiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new Request(config.chatModel(), List.of(new Message("user", prompt))))
                    .retrieve()
                    .onStatus(s -> s.isError(), (req, res) -> {
                        log.warn("Groq call failed with status {}", res.getStatusCode().value());
                        throw new ServiceUnavailableException(
                                "Fallback AI provider failed with status " + res.getStatusCode().value());
                    })
                    .body(Response.class);
        } catch (RestClientException e) {
            throw new ServiceUnavailableException("Fallback AI provider is unreachable", e);
        }
        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().getFirst().message() == null
                || response.choices().getFirst().message().content() == null) {
            throw new ServiceUnavailableException("Fallback AI provider returned an empty response");
        }
        return response.choices().getFirst().message().content();
    }

    record Request(String model, List<Message> messages) {
    }

    record Message(String role, String content) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Response(List<Choice> choices) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Choice(Message message) {
    }
}
