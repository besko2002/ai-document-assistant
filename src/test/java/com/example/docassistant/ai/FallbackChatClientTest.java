package com.example.docassistant.ai;

import com.example.docassistant.common.ServiceUnavailableException;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FallbackChatClientTest {

    private final GeminiChatClient gemini = mock(GeminiChatClient.class);
    private final GroqChatClient groq = mock(GroqChatClient.class);
    private final FallbackChatClient client = new FallbackChatClient(gemini, groq, 0);

    @Test
    void geminiIsUsedWhenItWorks() {
        when(gemini.complete("q")).thenReturn("from gemini");
        assertThat(client.complete("q")).isEqualTo("from gemini");
        verify(groq, never()).complete(anyString());
    }

    @Test
    void aBriefGeminiHiccupIsRetriedWithoutTouchingGroq() {
        when(gemini.complete("q")).thenThrow(new ServiceUnavailableException("503"))
                .thenThrow(new ServiceUnavailableException("503")).thenReturn("from gemini");
        assertThat(client.complete("q")).isEqualTo("from gemini");
        verify(gemini, times(3)).complete("q");
        verify(groq, never()).complete(anyString());
    }

    @Test
    void groqTakesOverWhenGeminiStaysDownAndGroqIsConfigured() {
        when(gemini.complete("q")).thenThrow(new ServiceUnavailableException("AI provider is rate-limited"));
        when(groq.isConfigured()).thenReturn(true);
        when(groq.complete("q")).thenReturn("from groq");
        assertThat(client.complete("q")).isEqualTo("from groq");
        verify(gemini, times(3)).complete("q");
    }

    @Test
    void theOriginalErrorSurfacesWhenThereIsNoFallbackKey() {
        when(gemini.complete("q")).thenThrow(new ServiceUnavailableException("AI provider is rate-limited"));
        when(groq.isConfigured()).thenReturn(false);
        assertThatThrownBy(() -> client.complete("q")).isInstanceOf(ServiceUnavailableException.class)
                .hasMessageContaining("rate-limited");
        verify(groq, never()).complete(anyString());
    }

    @Test
    void badInputIsNotRetriedOnTheFallback() {
        when(gemini.complete("")).thenThrow(new com.example.docassistant.common.BadRequestException("blank"));
        assertThatThrownBy(() -> client.complete("")).isInstanceOf(com.example.docassistant.common.BadRequestException.class);
        verify(groq, never()).complete(anyString());
    }
}
