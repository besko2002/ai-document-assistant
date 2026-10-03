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

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.ExpectedCount.times;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/** No network: the Gemini endpoint is a mock. A tiny vector size keeps the fixtures short. */
class GeminiEmbeddingClientTest {

    private static final String BASE = "https://gemini.test/v1beta";
    private static final String URL = BASE + "/models/emb:batchEmbedContents?key=k";

    private MockRestServiceServer server;
    private GeminiEmbeddingClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl(BASE);
        server = MockRestServiceServer.bindTo(builder).build();
        AiProperties props = new AiProperties(new AiProperties.Provider("k", BASE, "chat"),
                new AiProperties.Provider(null, null, null));
        client = new GeminiEmbeddingClient(builder.build(), props, "emb", 3, 0);
    }

    private static String vectors(int count) {
        return IntStream.range(0, count).mapToObj(i -> "{\"values\":[0.1,0.2," + i + ".5]}")
                .collect(Collectors.joining(",", "{\"embeddings\":[", "]}"));
    }

    @Test
    void documentsAreSentWithTheDocumentTaskTypeAndSizeAndParsedInOrder() {
        server.expect(requestTo(URL)).andExpect(method(HttpMethod.POST))
                .andExpect(jsonPath("$.requests.length()").value(2))
                .andExpect(jsonPath("$.requests[0].taskType").value("RETRIEVAL_DOCUMENT"))
                .andExpect(jsonPath("$.requests[0].outputDimensionality").value(3))
                .andExpect(jsonPath("$.requests[0].model").value("models/emb"))
                .andExpect(jsonPath("$.requests[1].content.parts[0].text").value("second"))
                .andRespond(withSuccess(vectors(2), MediaType.APPLICATION_JSON));

        List<float[]> result = client.embedDocuments(List.of("first", "second"));

        assertThat(result).hasSize(2);
        assertThat(result.get(1)).containsExactly(0.1f, 0.2f, 1.5f);
        server.verify();
    }

    @Test
    void queriesUseTheQueryTaskType() {
        server.expect(requestTo(URL)).andExpect(jsonPath("$.requests[0].taskType").value("RETRIEVAL_QUERY"))
                .andRespond(withSuccess(vectors(1), MediaType.APPLICATION_JSON));
        assertThat(client.embedQuery("what?")).hasSize(3);
        server.verify();
    }

    @Test
    void moreThan100PassagesAreSplitIntoBatches() {
        List<String> texts = IntStream.range(0, 150).mapToObj(i -> "t" + i).toList();
        server.expect(requestTo(URL)).andExpect(jsonPath("$.requests.length()").value(100))
                .andRespond(withSuccess(vectors(100), MediaType.APPLICATION_JSON));
        server.expect(requestTo(URL)).andExpect(jsonPath("$.requests.length()").value(50))
                .andRespond(withSuccess(vectors(50), MediaType.APPLICATION_JSON));
        assertThat(client.embedDocuments(texts)).hasSize(150);
        server.verify();
    }

    @Test
    void aRateLimitIsRetriedThenSucceeds() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        server.expect(requestTo(URL)).andRespond(withSuccess(vectors(1), MediaType.APPLICATION_JSON));
        assertThat(client.embedQuery("x")).hasSize(3);
        server.verify();
    }

    @Test
    void persistentRateLimitBecomesServiceUnavailable() {
        server.expect(times(4), requestTo(URL)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        assertThatThrownBy(() -> client.embedQuery("x")).isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("rate-limited");
        server.verify();
    }

    @Test
    void serverErrorsAndWrongSizedVectorsAreRejected() {
        server.expect(requestTo(URL)).andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));
        server.expect(requestTo(URL)).andRespond(withSuccess("{\"embeddings\":[{\"values\":[0.1]}]}",
                MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> client.embedQuery("x")).isInstanceOf(ServiceUnavailableException.class);
        assertThatThrownBy(() -> client.embedQuery("x")).isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("wrong size");
    }

    @Test
    void missingKeyFailsBeforeAnyRequest() {
        AiProperties noKey = new AiProperties(new AiProperties.Provider("", BASE, "chat"),
                new AiProperties.Provider(null, null, null));
        GeminiEmbeddingClient c = new GeminiEmbeddingClient(RestClient.builder().baseUrl(BASE).build(), noKey,
                "emb", 3, 0);
        assertThatThrownBy(() -> c.embedQuery("x")).isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("not configured");
    }
}
