package com.quarkrag.store;

import com.quarkrag.embed.HashingEmbedder;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Brute-force cosine search over a concurrent map.
 *
 * <p>Exact rather than approximate, which is the right trade-off at small
 * corpus sizes: an HNSW index only pays off once linear scan stops being
 * instant. Swapping to pgvector's HNSW index is a config change, not a rewrite.
 */
public class InMemoryVectorStore implements VectorStore {

    private final Map<String, Record> records = new ConcurrentHashMap<>();

    @Override
    public void upsertDocument(String docId, List<Record> chunks) {
        deleteDocument(docId);
        for (Record chunk : chunks) {
            records.put(chunk.id(), chunk);
        }
    }

    @Override
    public int deleteDocument(String docId) {
        List<String> ids = records.values().stream()
                .filter(r -> r.docId().equals(docId))
                .map(Record::id)
                .toList();
        ids.forEach(records::remove);
        return ids.size();
    }

    @Override
    public List<Hit> search(float[] query, int k) {
        List<Hit> hits = new ArrayList<>(records.size());
        for (Record r : records.values()) {
            hits.add(new Hit(r.id(), r.docId(), r.text(),
                    round4(HashingEmbedder.cosine(query, r.embedding()))));
        }
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());
        return hits.subList(0, Math.min(k, hits.size()));
    }

    @Override
    public long chunkCount() {
        return records.size();
    }

    @Override
    public long documentCount() {
        Set<String> docs = new HashSet<>();
        for (Record r : records.values()) {
            docs.add(r.docId());
        }
        return docs.size();
    }

    @Override
    public String name() {
        return "memory";
    }

    private static double round4(double v) {
        return Math.round(v * 10_000.0) / 10_000.0;
    }
}
