package com.example.docassistant.rag;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/** One question and the answer given for it; {@code sources} is the cited passages as JSON. */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    private UUID id;

    @Column(name = "document_id", nullable = false)
    private UUID documentId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(nullable = false, columnDefinition = "text")
    private String question;

    @Column(nullable = false, columnDefinition = "text")
    private String answer;

    @Column(nullable = false, columnDefinition = "text")
    private String sources;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ChatMessage() {
    }

    public ChatMessage(UUID documentId, UUID userId, String question, String answer, String sources) {
        this.id = UUID.randomUUID();
        this.documentId = documentId;
        this.userId = userId;
        this.question = question;
        this.answer = answer;
        this.sources = sources;
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }
    public String getQuestion() { return question; }
    public String getAnswer() { return answer; }
    public String getSources() { return sources; }
    public Instant getCreatedAt() { return createdAt; }
}
