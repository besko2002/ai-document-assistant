-- Phase 3: vector search over chunks and the question/answer history.
-- The extension is created here too, so a plain pgvector image (e.g. in tests) works without an init script.
CREATE EXTENSION IF NOT EXISTS vector;

-- 768 dimensions = gemini-embedding-001 with outputDimensionality 768.
-- Nullable: documents uploaded in phase 2 have no embedding and are skipped by the search.
ALTER TABLE document_chunks ADD COLUMN embedding vector(768);

CREATE INDEX idx_chunks_embedding ON document_chunks USING hnsw (embedding vector_cosine_ops);

CREATE TABLE chat_messages
(
    id          UUID PRIMARY KEY,
    document_id UUID                     NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    user_id     UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    question    TEXT                     NOT NULL,
    answer      TEXT                     NOT NULL,
    sources     TEXT                     NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_chat_messages_doc ON chat_messages (document_id, user_id, created_at);
