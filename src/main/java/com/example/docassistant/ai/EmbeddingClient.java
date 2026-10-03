package com.example.docassistant.ai;

import java.util.List;

/** Turns text into vectors, so passages can be compared by meaning instead of by words. */
public interface EmbeddingClient {

    /** Embeds passages to be searched; the result has one vector per text, in the same order. */
    List<float[]> embedDocuments(List<String> texts);

    /** Embeds a user question to search with. */
    float[] embedQuery(String text);
}
