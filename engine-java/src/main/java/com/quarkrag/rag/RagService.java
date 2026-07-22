package com.quarkrag.rag;

import com.quarkrag.chunk.SemanticChunker;
import com.quarkrag.embed.HashingEmbedder;
import com.quarkrag.llm.LlmClient;
import com.quarkrag.store.VectorStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.ArrayList;
import java.util.List;

/** Orchestrates ingest (chunk → embed → store) and query (embed → search → synthesise). */
@ApplicationScoped
public class RagService {

    @Inject
    SemanticChunker chunker;

    @Inject
    HashingEmbedder embedder;

    @Inject
    VectorStore store;

    @Inject
    LlmClient llm;

    @ConfigProperty(name = "quarkrag.chunk.max-words", defaultValue = "120")
    int maxWords;

    public record IngestResult(String docId, int chunks, int embeddingDim, double tookMs) {
    }

    public record Context(String id, String docId, String text, double score) {
    }

    public record QueryResult(String answer, List<Context> contexts, String model, double tookMs) {
    }

    public IngestResult ingest(String docId, String text) {
        long start = System.nanoTime();

        List<SemanticChunker.Chunk> chunks = chunker.chunk(text, maxWords);
        List<VectorStore.Record> records = new ArrayList<>(chunks.size());
        for (SemanticChunker.Chunk chunk : chunks) {
            records.add(new VectorStore.Record(
                    docId + "#" + chunk.index(),
                    docId,
                    chunk.text(),
                    embedder.embed(chunk.text())));
        }
        store.upsertDocument(docId, records);

        return new IngestResult(docId, records.size(), embedder.dimensions(), elapsedMs(start));
    }

    public QueryResult query(String question, int k) {
        long start = System.nanoTime();

        float[] queryVector = embedder.embed(question);
        List<VectorStore.Hit> hits = store.search(queryVector, Math.max(k, 1));
        String answer = llm.answer(question, hits);

        List<Context> contexts = new ArrayList<>(hits.size());
        for (VectorStore.Hit hit : hits) {
            contexts.add(new Context(hit.id(), hit.docId(), hit.text(), hit.score()));
        }
        return new QueryResult(answer, contexts, llm.id(), elapsedMs(start));
    }

    public int delete(String docId) {
        return store.deleteDocument(docId);
    }

    public VectorStore store() {
        return store;
    }

    public HashingEmbedder embedder() {
        return embedder;
    }

    public LlmClient llm() {
        return llm;
    }

    private static double elapsedMs(long startNanos) {
        return Math.round((System.nanoTime() - startNanos) / 1_000_000.0 * 100.0) / 100.0;
    }
}
