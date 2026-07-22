package com.quarkrag.store;

import java.util.List;

/**
 * Storage and retrieval of chunk embeddings.
 *
 * <p>Two implementations ship: an in-memory brute-force store (default, no
 * services) and a pgvector store backed by Postgres. The interface is
 * deliberately narrow — upsert a document's chunks, delete a document, search
 * by vector — which is exactly what any vector database offers.
 */
public interface VectorStore {

    record Record(String id, String docId, String text, float[] embedding) {
    }

    record Hit(String id, String docId, String text, double score) {
    }

    /** Replaces every chunk belonging to {@code docId}. */
    void upsertDocument(String docId, List<Record> chunks);

    /** Removes every chunk for a document; returns how many were removed. */
    int deleteDocument(String docId);

    /** Top-{@code k} chunks by cosine similarity. */
    List<Hit> search(float[] query, int k);

    long chunkCount();

    long documentCount();

    /** Identifier reported by {@code /api/stats}. */
    String name();
}
