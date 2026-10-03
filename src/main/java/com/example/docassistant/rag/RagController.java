package com.example.docassistant.rag;

import com.example.docassistant.rag.RagService.Answer;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents/{documentId}")
@Tag(name = "Chat", description = "Ask questions about one of your documents")
class RagController {

    record AskRequest(@NotBlank @Size(max = 1000) String question) {
    }

    private final RagService rag;

    RagController(RagService rag) {
        this.rag = rag;
    }

    @PostMapping("/ask")
    Answer ask(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID documentId, @Valid @RequestBody AskRequest body) {
        return rag.ask(UUID.fromString(jwt.getSubject()), documentId, body.question());
    }

    @GetMapping("/messages")
    List<Answer> messages(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID documentId) {
        return rag.history(UUID.fromString(jwt.getSubject()), documentId);
    }
}
