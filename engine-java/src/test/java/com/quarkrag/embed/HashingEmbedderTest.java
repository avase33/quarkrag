package com.quarkrag.embed;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HashingEmbedderTest {

    private final HashingEmbedder embedder = new HashingEmbedder(256);

    @Test
    void isDeterministicAndNormalised() {
        float[] a = embedder.embed("database index query performance");
        float[] b = embedder.embed("database index query performance");

        assertArrayEquals(a, b, 0.0f);

        double norm = 0.0;
        for (float v : a) {
            norm += (double) v * v;
        }
        assertEquals(1.0, Math.sqrt(norm), 1e-6);
    }

    @Test
    void similarityReflectsContent() {
        double same = HashingEmbedder.cosine(
                embedder.embed("database index query"),
                embedder.embed("database index query"));
        double different = HashingEmbedder.cosine(
                embedder.embed("database index query"),
                embedder.embed("cats dogs kittens"));

        assertTrue(same > 0.99, "identical text should be ~1.0, was " + same);
        // unrelated text shares no terms; only a rare hash collision could lift this
        assertTrue(different < 0.5, "unrelated text scored " + different);
        assertTrue(same > different + 0.4);
    }

    @Test
    void partialOverlapScoresBetween() {
        double overlap = HashingEmbedder.cosine(
                embedder.embed("database index query performance"),
                embedder.embed("database index tuning"));

        assertTrue(overlap > 0.2 && overlap < 0.99, "partial overlap was " + overlap);
    }

    @Test
    void emptyTextGivesZeroVector() {
        float[] v = embedder.embed("");
        for (float f : v) {
            assertEquals(0.0f, f);
        }
    }

    @Test
    void pgVectorLiteralRoundTrips() {
        float[] original = embedder.embed("round trip through postgres");
        float[] parsed = HashingEmbedder.fromPgVector(HashingEmbedder.toPgVector(original));

        assertEquals(original.length, parsed.length);
        assertArrayEquals(original, parsed, 1e-6f);
    }

    @Test
    void dimensionIsConfigurable() {
        assertEquals(64, new HashingEmbedder(64).embed("anything at all").length);
    }
}
