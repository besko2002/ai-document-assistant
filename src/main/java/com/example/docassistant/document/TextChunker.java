package com.example.docassistant.document;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits the text of one page into overlapping pieces small enough to embed and to fit in a prompt.
 * Pieces end at a word boundary when possible, and each one repeats the last {@code overlap}
 * characters of the previous piece so a sentence cut by a boundary is still found whole in one of them.
 */
public final class TextChunker {

    private TextChunker() {
    }

    public static List<String> split(String raw, int size, int overlap) {
        if (size < 50 || overlap < 0 || overlap >= size / 2) {
            throw new IllegalArgumentException("size must be >= 50 and overlap must be below size / 2");
        }
        if (raw == null) {
            return List.of();
        }
        String text = raw.replaceAll("\\s+", " ").trim();
        if (text.isEmpty()) {
            return List.of();
        }
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + size, text.length());
            if (end < text.length()) {
                int lastSpace = text.lastIndexOf(' ', end);
                if (lastSpace > start + size / 2) {
                    end = lastSpace;
                }
            }
            String piece = text.substring(start, end).trim();
            if (!piece.isEmpty()) {
                chunks.add(piece);
            }
            if (end >= text.length()) {
                break;
            }
            int next = Math.max(end - overlap, start + 1);
            // Begin the next piece at a word start rather than in the middle of a word.
            int space = text.indexOf(' ', next);
            start = (space != -1 && space < end) ? space + 1 : next;
        }
        return chunks;
    }
}
