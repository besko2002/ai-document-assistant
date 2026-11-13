package com.example.docassistant.rag;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/** Stores chunk embeddings in pgvector and finds the chunks closest to a question. */
@Repository
public class ChunkVectorStore {

    /** A matching chunk with its cosine similarity (1 = same direction, 0 = unrelated). */
    public record Match(UUID chunkId, int pageNumber, String content, double score) {
    }

    private final JdbcTemplate jdbc;

    ChunkVectorStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void saveEmbeddings(List<UUID> chunkIds, List<float[]> vectors) {
        if (chunkIds.size() != vectors.size()) {
            throw new IllegalArgumentException("One vector is needed per chunk");
        }
        jdbc.batchUpdate("UPDATE document_chunks SET embedding = ?::vector WHERE id = ?", chunkIds.size() == 0
                ? List.<Object[]>of()
                : java.util.stream.IntStream.range(0, chunkIds.size())
                        .mapToObj(i -> new Object[]{literal(vectors.get(i)), chunkIds.get(i)}).toList());
    }

    /** The {@code limit} chunks of one document nearest to the question, best first. */
    public List<Match> search(UUID documentId, float[] question, int limit) {
        String vector = literal(question);
        return jdbc.query("""
                        SELECT id, page_number, content, 1 - (embedding <=> ?::vector) AS score
                        FROM document_chunks
                        WHERE document_id = ? AND embedding IS NOT NULL
                        ORDER BY embedding <=> ?::vector
                        LIMIT ?""",
                (rs, i) -> new Match(rs.getObject("id", UUID.class), rs.getInt("page_number"),
                        rs.getString("content"), rs.getDouble("score")),
                vector, documentId, vector, limit);
    }

    /** pgvector's text format: {@code [0.1,0.2,...]}. */
    static String literal(float[] v) {
        StringBuilder sb = new StringBuilder(v.length * 9).append('[');
        for (int i = 0; i < v.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(v[i]);
        }
        return sb.append(']').toString();
    }
}
