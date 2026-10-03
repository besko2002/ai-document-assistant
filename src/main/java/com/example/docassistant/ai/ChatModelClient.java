package com.example.docassistant.ai;

/** A chat completion provider. Phase 3 will add a fallback implementation on top of this. */
public interface ChatModelClient {

    /**
     * Sends a single prompt and returns the model's answer text.
     *
     * @throws com.example.docassistant.common.ServiceUnavailableException when the provider
     *         is rate-limited, misconfigured, or returns an unusable response.
     */
    String complete(String prompt);
}
