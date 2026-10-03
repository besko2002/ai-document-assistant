-- Phase 2: accounts, uploaded documents and their text chunks.
-- Embeddings (pgvector) are added to document_chunks in phase 3.

CREATE TABLE users
(
    id            UUID PRIMARY KEY,
    name          VARCHAR(100)             NOT NULL,
    email         VARCHAR(255)             NOT NULL UNIQUE,
    password_hash VARCHAR(100)             NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE TABLE documents
(
    id          UUID PRIMARY KEY,
    user_id     UUID                     NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    filename    VARCHAR(255)             NOT NULL,
    size_bytes  BIGINT                   NOT NULL CHECK (size_bytes > 0),
    page_count  INT                      NOT NULL CHECK (page_count > 0),
    chunk_count INT                      NOT NULL CHECK (chunk_count > 0),
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT now()
);

CREATE INDEX idx_documents_user ON documents (user_id, created_at DESC);

CREATE TABLE document_chunks
(
    id          UUID PRIMARY KEY,
    document_id UUID NOT NULL REFERENCES documents (id) ON DELETE CASCADE,
    chunk_index INT  NOT NULL,
    page_number INT  NOT NULL,
    content     TEXT NOT NULL,
    UNIQUE (document_id, chunk_index)
);
