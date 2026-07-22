package com.quarkrag.rag;

import com.quarkrag.chunk.SemanticChunker;
import com.quarkrag.embed.HashingEmbedder;
import com.quarkrag.llm.ExtractiveLlm;
import com.quarkrag.store.InMemoryVectorStore;
import com.quarkrag.store.VectorStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exercises chunk → embed → store → retrieve → synthesise without starting
 * Quarkus, so it stays a fast unit test.
 */
class RagPipelineTest {

    private static final String PTO_DOC = """
            Employees accrue paid time off every month and can carry a limited balance
            into the next year.

            To request time off, open the HR portal and submit a leave request with your
            dates. Your manager approves or declines within two business days.

            Sick leave is separate from vacation and does not require advance notice.
            """;

    private static final String DEPLOY_DOC = """
            We ship from the main branch. Every merge triggers continuous integration,
            and a green build is promoted to staging automatically.

            Production deploys are manual and require a second approver.
            """;

    private final SemanticChunker chunker = new SemanticChunker();
    private final HashingEmbedder embedder = new HashingEmbedder(256);
    private final ExtractiveLlm llm = new ExtractiveLlm();
    private VectorStore store;

    @BeforeEach
    void setUp() {
        store = new InMemoryVectorStore();
        index("hr/pto.md", PTO_DOC);
        index("eng/deploys.md", DEPLOY_DOC);
    }

    private void index(String docId, String text) {
        List<VectorStore.Record> records = new ArrayList<>();
        for (SemanticChunker.Chunk chunk : chunker.chunk(text, 120)) {
            records.add(new VectorStore.Record(
                    docId + "#" + chunk.index(), docId, chunk.text(), embedder.embed(chunk.text())));
        }
        store.upsertDocument(docId, records);
    }

    @Test
    void indexesBothDocuments() {
        assertEquals(2, store.documentCount());
        assertTrue(store.chunkCount() >= 2);
    }

    @Test
    void retrievesTheRightDocument() {
        List<VectorStore.Hit> hits = store.search(embedder.embed("how do I request time off?"), 3);

        assertFalse(hits.isEmpty());
        assertEquals("hr/pto.md", hits.get(0).docId(), "top hit should come from the PTO doc");
        assertTrue(hits.get(0).score() > 0.0);
    }

    @Test
    void answersFromTheRetrievedContext() {
        List<VectorStore.Hit> hits = store.search(embedder.embed("how do I request time off?"), 3);
        String answer = llm.answer("how do I request time off?", hits).toLowerCase();

        assertTrue(answer.contains("portal"), "answer was: " + answer);
        assertTrue(answer.contains("leave"), "answer was: " + answer);
    }

    @Test
    void answersAreGroundedInTheCorpus() {
        List<VectorStore.Hit> hits = store.search(embedder.embed("who approves production deploys?"), 3);
        String answer = llm.answer("who approves production deploys?", hits);

        // extractive synthesis can only emit text that exists in a retrieved chunk
        boolean grounded = hits.stream().anyMatch(h -> h.text().contains(answer.split("\\.")[0].trim()));
        assertTrue(grounded, "answer must be verbatim from context: " + answer);
    }

    @Test
    void admitsWhenTheCorpusHasNoAnswer() {
        List<VectorStore.Hit> hits = store.search(embedder.embed("what is the airspeed of a swallow?"), 3);
        String answer = llm.answer("what is the airspeed of a swallow?", hits);

        assertTrue(answer.startsWith("I don't have information"), "answer was: " + answer);
    }

    @Test
    void deletingADocumentRemovesItsChunks() {
        long before = store.chunkCount();
        int removed = store.deleteDocument("hr/pto.md");

        assertTrue(removed > 0);
        assertEquals(before - removed, store.chunkCount());
        assertEquals(1, store.documentCount());
    }

    @Test
    void reindexingReplacesRatherThanDuplicates() {
        long before = store.chunkCount();
        index("hr/pto.md", PTO_DOC);

        assertEquals(before, store.chunkCount(), "upsert must replace a document's chunks");
    }
}
