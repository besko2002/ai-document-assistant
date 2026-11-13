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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GroqChatClientTest {

    private static final String BASE = "https://groq.test/openai/v1";

    private MockRestServiceServer server;
    private GroqChatClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        client = new GroqChatClient(builder.build(), new AiProperties(new AiProperties.Provider(null, null, null),
                new AiProperties.Provider("gk", BASE, "llama-x")));
    }

    @Test
    void sendsAChatCompletionWithTheBearerKeyAndReadsTheReply() {
        server.expect(requestTo(BASE + "/chat/completions")).andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer gk"))
                .andExpect(jsonPath("$.model").value("llama-x"))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("hi"))
                .andRespond(withSuccess("{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":\"hello\"}}]}",
                        MediaType.APPLICATION_JSON));
        assertThat(client.complete("hi")).isEqualTo("hello");
        server.verify();
    }

    @Test
    void errorsAndEmptyRepliesBecomeServiceUnavailable() {
        server.expect(requestTo(BASE + "/chat/completions")).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(BASE + "/chat/completions"))
                .andRespond(withSuccess("{\"choices\":[]}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.complete("hi")).isInstanceOf(ServiceUnavailableException.class);
        assertThatThrownBy(() -> client.complete("hi")).isInstanceOf(ServiceUnavailableException.class);
    }

    @Test
    void isNotConfiguredWithoutAKey() {
        GroqChatClient none = new GroqChatClient(RestClient.builder().baseUrl(BASE).build(),
                new AiProperties(null, null));
        assertThat(none.isConfigured()).isFalse();
        assertThatThrownBy(() -> none.complete("hi")).isInstanceOf(ServiceUnavailableException.class);
    }
}
