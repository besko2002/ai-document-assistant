package com.example.docassistant.document;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** An uploaded PDF owned by one user. */
@Entity
@Table(name = "documents")
public class Document {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, length = 255)
    private String filename;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @Column(name = "chunk_count", nullable = false)
    private int chunkCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Document() {
    }

    public Document(UUID userId, String filename, long sizeBytes, int pageCount, int chunkCount) {
        this.id = UUID.randomUUID();
        this.userId = userId;
        this.filename = filename;
        this.sizeBytes = sizeBytes;
        this.pageCount = pageCount;
        this.chunkCount = chunkCount;
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public String getFilename() {
        return filename;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public int getPageCount() {
        return pageCount;
    }

    public int getChunkCount() {
        return chunkCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
