package com.example.docassistant.document;

import com.example.docassistant.ai.EmbeddingClient;
import com.example.docassistant.common.BadRequestException;
import com.example.docassistant.common.ResourceNotFoundException;
import com.example.docassistant.document.PdfTextExtractor.PageText;
import com.example.docassistant.rag.ChunkVectorStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Stores uploaded PDFs as text chunks. Every read and delete is scoped to the owner. */
@Service
public class DocumentService {

    private static final byte[] PDF_MAGIC = "%PDF-".getBytes(StandardCharsets.US_ASCII);

    private final DocumentRepository documents;
    private final DocumentChunkRepository chunks;
    private final PdfTextExtractor extractor;
    private final EmbeddingClient embeddings;
    private final ChunkVectorStore vectors;
    private final int chunkSize;
    private final int chunkOverlap;

    DocumentService(DocumentRepository documents, DocumentChunkRepository chunks, PdfTextExtractor extractor,
                    EmbeddingClient embeddings, ChunkVectorStore vectors,
                    @Value("${app.documents.chunk-size:1000}") int chunkSize,
                    @Value("${app.documents.chunk-overlap:150}") int chunkOverlap) {
        this.documents = documents;
        this.chunks = chunks;
        this.extractor = extractor;
        this.embeddings = embeddings;
        this.vectors = vectors;
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    @Transactional
    public Document upload(UUID userId, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BadRequestException("Choose a PDF file to upload");
        }
        byte[] bytes = read(file);
        if (!looksLikePdf(bytes)) {
            throw new BadRequestException("Only PDF files are supported");
        }

        List<PageText> pages = extractor.extract(bytes);
        List<PageText> pieces = new ArrayList<>();
        for (PageText page : pages) {
            for (String text : TextChunker.split(page.text(), chunkSize, chunkOverlap)) {
                pieces.add(new PageText(page.number(), text));
            }
        }
        if (pieces.isEmpty()) {
            throw new BadRequestException("No text could be extracted. Scanned or image-only PDFs are not supported");
        }

        // Embed first: if the AI provider fails, nothing has been saved yet.
        List<float[]> pieceVectors = embeddings.embedDocuments(pieces.stream().map(PageText::text).toList());

        Document document = documents.save(new Document(
                userId, cleanName(file.getOriginalFilename()), bytes.length, pages.size(), pieces.size()));
        List<DocumentChunk> rows = new ArrayList<>(pieces.size());
        for (int i = 0; i < pieces.size(); i++) {
            rows.add(new DocumentChunk(document.getId(), i, pieces.get(i).number(), pieces.get(i).text()));
        }
        chunks.saveAllAndFlush(rows);
        vectors.saveEmbeddings(rows.stream().map(DocumentChunk::getId).toList(), pieceVectors);
        return document;
    }

    @Transactional(readOnly = true)
    public List<Document> list(UUID userId) {
        return documents.findByUserIdOrderByCreatedAtDesc(userId);
    }

    @Transactional(readOnly = true)
    public Document get(UUID userId, UUID documentId) {
        return documents.findByIdAndUserId(documentId, userId)
                .orElseThrow(() -> new ResourceNotFoundException("Document " + documentId + " was not found"));
    }

    @Transactional
    public void delete(UUID userId, UUID documentId) {
        // Someone else's document is reported as missing, so ids do not leak between users.
        documents.delete(get(userId, documentId));
    }

    private static byte[] read(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException e) {
            throw new BadRequestException("The uploaded file could not be read");
        }
    }

    private static boolean looksLikePdf(byte[] bytes) {
        if (bytes.length < PDF_MAGIC.length) {
            return false;
        }
        for (int i = 0; i < PDF_MAGIC.length; i++) {
            if (bytes[i] != PDF_MAGIC[i]) {
                return false;
            }
        }
        return true;
    }

    private static String cleanName(String original) {
        String name = original == null ? "document.pdf" : original.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).trim();
        if (name.isEmpty()) {
            name = "document.pdf";
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }
}
