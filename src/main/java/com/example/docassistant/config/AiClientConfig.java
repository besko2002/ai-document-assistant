package com.example.docassistant.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
public class AiClientConfig {

    private static final String DEFAULT_GEMINI_BASE_URL =
            "https://generativelanguage.googleapis.com/v1beta";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(5);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(60);

    @Bean
    public RestClient geminiRestClient(RestClient.Builder builder, AiProperties properties) {
        String baseUrl = properties.gemini().baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = DEFAULT_GEMINI_BASE_URL;
        }

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);

        return builder.clone()
                .baseUrl(baseUrl)
                .requestFactory(requestFactory)
                .build();
    }

    /** OpenAI-compatible endpoint of the Groq fallback provider. */
    @Bean
    public RestClient groqRestClient(RestClient.Builder builder, AiProperties properties) {
        String baseUrl = properties.groq().baseUrl();
        if (baseUrl == null || baseUrl.isBlank()) {
            baseUrl = "https://api.groq.com/openai/v1";
        }
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        return builder.clone().baseUrl(baseUrl).requestFactory(requestFactory).build();
    }
}
