package com.quarkrag.llm;

import com.quarkrag.store.VectorStore;

import java.util.List;

/**
 * Turns a question plus retrieved context into an answer.
 *
 * <p>The offline implementation is extractive — it selects and stitches the
 * most relevant sentences from the retrieved chunks. That keeps the engine
 * runnable with no API key and no model weights while still exercising the
 * whole retrieval path, and it can never hallucinate: every sentence in the
 * answer came verbatim from an indexed document.
 */
public interface LlmClient {

    String answer(String question, List<VectorStore.Hit> contexts);

    /** Identifier surfaced in the API response, e.g. {@code extractive}. */
    String id();
}
