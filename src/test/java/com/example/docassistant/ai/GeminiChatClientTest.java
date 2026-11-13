package com.example.docassistant.ai;

import com.example.docassistant.common.ServiceUnavailableException;
import com.example.docassistant.config.AiProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** Pure unit test — no real network calls are made. */
class GeminiChatClientTest {

    private static final String BASE_URL = "https://gemini.test/v1beta";
    private static final String MODEL = "gemini-3.8-flash";
    private static final String API_KEY = "test-key";

    private MockRestServiceServer server;
    private GeminiChatClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE_URL);
        server = MockRestServiceServer.bindTo(builder).build();
        AiProperties properties = new AiProperties(
                new AiProperties.Provider(API_KEY, BASE_URL, MODEL),
                new AiProperties.Provider(null, null, null));
        client = new GeminiChatClient(builder.build(), properties);
    }

    @Test
    void sendsGenerateContentRequestAndParsesTheAnswerText() {
        String responseBody = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [ { "text": "The document is about pgvector." } ],
                        "role": "model"
                      },
                      "finishReason": "STOP"
                    }
                  ],
                  "usageMetadata": { "totalTokenCount": 42 }
                }
                """;

        server.expect(once(), requestTo(BASE_URL + "/models/" + MODEL + ":generateContent?key=" + API_KEY))
                .andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.contents[0].parts[0].text").value("What is this document about?"))
                .andRespond(withSuccess(responseBody, MediaType.APPLICATION_JSON));

        String answer = client.complete("What is this document about?");

        assertThat(answer).isEqualTo("The document is about pgvector.");
        server.verify();
    }

    @Test
    void rateLimitBecomesServiceUnavailable() {
        server.expect(once(), requestTo(BASE_URL + "/models/" + MODEL + ":generateContent?key=" + API_KEY))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.complete("anything"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("AI provider is rate-limited");

        server.verify();
    }

    @Test
    void emptyCandidateListBecomesServiceUnavailable() {
        server.expect(once(), requestTo(BASE_URL + "/models/" + MODEL + ":generateContent?key=" + API_KEY))
                .andRespond(withSuccess("{\"candidates\":[]}", MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.complete("anything"))
                .isInstanceOf(ServiceUnavailableException.class)
                .hasMessage("AI provider returned an empty response");

        server.verify();
    }
}
