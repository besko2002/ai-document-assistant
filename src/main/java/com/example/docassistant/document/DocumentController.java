package com.example.docassistant.document;

import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/documents")
@Tag(name = "Documents", description = "Upload PDFs and manage your own documents")
class DocumentController {

    record DocumentResponse(UUID id, String filename, long sizeBytes, int pageCount, int chunkCount,
                            Instant createdAt) {
        static DocumentResponse from(Document d) {
            return new DocumentResponse(d.getId(), d.getFilename(), d.getSizeBytes(), d.getPageCount(),
                    d.getChunkCount(), d.getCreatedAt());
        }
    }

    private final DocumentService documents;

    DocumentController(DocumentService documents) {
        this.documents = documents;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    ResponseEntity<DocumentResponse> upload(@AuthenticationPrincipal Jwt jwt,
                                            @RequestParam(name = "file", required = false) MultipartFile file) {
        Document saved = documents.upload(userId(jwt), file);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}")
                .buildAndExpand(saved.getId()).toUri();
        return ResponseEntity.created(location).body(DocumentResponse.from(saved));
    }

    @GetMapping
    List<DocumentResponse> list(@AuthenticationPrincipal Jwt jwt) {
        return documents.list(userId(jwt)).stream().map(DocumentResponse::from).toList();
    }

    @GetMapping("/{id}")
    DocumentResponse get(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        return DocumentResponse.from(documents.get(userId(jwt), id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void delete(@AuthenticationPrincipal Jwt jwt, @PathVariable UUID id) {
        documents.delete(userId(jwt), id);
    }

    private static UUID userId(Jwt jwt) {
        return UUID.fromString(jwt.getSubject());
    }
}
