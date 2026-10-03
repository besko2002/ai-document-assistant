package com.example.docassistant;

import com.example.docassistant.ai.EmbeddingClient;
import com.example.docassistant.common.ServiceUnavailableException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Replaces the Gemini embeddings in integration tests (no network, no quota). It is a bag-of-words hash:
 * texts that share words get similar vectors, so the real pgvector search still ranks them meaningfully.
 */
@TestConfiguration
public class FakeEmbeddingConfig {

    public static final int DIMENSIONS = 768;

    public static class FakeEmbeddingClient implements EmbeddingClient {

        /** Flip to simulate the AI provider being down. */
        public volatile boolean failing;

        @Override
        public List<float[]> embedDocuments(List<String> texts) {
            check();
            return texts.stream().map(FakeEmbeddingClient::vector).toList();
        }

        @Override
        public float[] embedQuery(String text) {
            check();
            return vector(text);
        }

        private void check() {
            if (failing) {
                throw new ServiceUnavailableException("AI provider is unreachable");
            }
        }

        static float[] vector(String text) {
            float[] v = new float[DIMENSIONS];
            Matcher m = Pattern.compile("[a-z0-9]+").matcher(text.toLowerCase(Locale.ROOT));
            while (m.find()) {
                v[Math.floorMod(m.group().hashCode(), DIMENSIONS)] += 1f;
            }
            double norm = 0;
            for (float x : v) {
                norm += x * x;
            }
            norm = Math.sqrt(norm);
            if (norm == 0) {
                v[0] = 1f;
                return v;
            }
            for (int i = 0; i < v.length; i++) {
                v[i] /= (float) norm;
            }
            return v;
        }
    }

    @Bean
    @Primary
    FakeEmbeddingClient fakeEmbeddingClient() {
        return new FakeEmbeddingClient();
    }
}
