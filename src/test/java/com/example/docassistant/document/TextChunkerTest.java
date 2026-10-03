package com.example.docassistant.document;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TextChunkerTest {

    private static String words(int count) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < count; i++) {
            sb.append("word").append(i).append(' ');
        }
        return sb.toString();
    }

    @Test
    void shortTextIsOneChunk() {
        assertThat(TextChunker.split("  Hello   world \n again ", 1000, 100)).containsExactly("Hello world again");
    }

    @Test
    void blankOrNullGivesNoChunks() {
        assertThat(TextChunker.split("   \n\t ", 1000, 100)).isEmpty();
        assertThat(TextChunker.split(null, 1000, 100)).isEmpty();
    }

    @Test
    void longTextIsSplitAndEveryChunkRespectsTheSize() {
        List<String> chunks = TextChunker.split(words(500), 200, 40);
        assertThat(chunks.size()).isGreaterThan(5);
        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(200));
    }

    @Test
    void chunksBreakOnWordBoundariesAndOverlap() {
        List<String> chunks = TextChunker.split(words(300), 200, 40);
        for (int i = 0; i < chunks.size() - 1; i++) {
            String current = chunks.get(i);
            String next = chunks.get(i + 1);
            // A cut never lands inside a word...
            assertThat(current).matches(".*word\\d+$");
            assertThat(next).startsWith("word");
            // ...and the neighbours share the words of the overlap.
            String lastWord = current.substring(current.lastIndexOf(' ') + 1);
            assertThat(next).contains(lastWord);
        }
    }

    @Test
    void everyWordSurvivesChunking() {
        // Padded with spaces so the first and last words are matched as whole words too.
        String joined = " " + String.join(" ", TextChunker.split(words(400), 200, 40)) + " ";
        for (int i = 0; i < 400; i++) {
            assertThat(joined).contains(" word" + i + " ");
        }
    }

    @Test
    void unbrokenTextWithoutSpacesStillTerminates() {
        List<String> chunks = TextChunker.split("x".repeat(2500), 1000, 100);
        assertThat(chunks).hasSizeGreaterThanOrEqualTo(3);
        assertThat(chunks).allSatisfy(c -> assertThat(c.length()).isLessThanOrEqualTo(1000));
    }

    @Test
    void invalidSettingsAreRejected() {
        assertThatThrownBy(() -> TextChunker.split("text", 10, 0)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TextChunker.split("text", 100, 60)).isInstanceOf(IllegalArgumentException.class);
    }
}
