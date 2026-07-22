package com.quarkrag.embed;

import com.quarkrag.text.Text;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A from-scratch text embedder using the <b>hashing trick</b>.
 *
 * <p>Each term is hashed into one of {@code dim} buckets; a second bit of the
 * same hash gives the term a {@code +/-} sign, which makes collisions cancel
 * out on average instead of always inflating a bucket. Terms are weighted by
 * sublinear term frequency {@code 1 + log(tf)}, and the vector is L2 normalised
 * so cosine similarity reduces to a dot product.
 *
 * <p>It uses {@link MessageDigest} rather than {@code String.hashCode()} so the
 * embedding is stable across JVM runs and across machines — a vector written to
 * Postgres today must still match a query embedded tomorrow.
 *
 * <p>This is deliberately not a neural embedder: it needs no model weights, no
 * GPU, and no network, which is what lets the whole engine start in
 * milliseconds and compile to a native binary. Swap in a real embedding model
 * by implementing this one method.
 */
@ApplicationScoped
public class HashingEmbedder {

    private final int dim;

    public HashingEmbedder(@ConfigProperty(name = "quarkrag.embedding.dim", defaultValue = "256") int dim) {
        this.dim = dim;
    }

    public int dimensions() {
        return dim;
    }

    public float[] embed(String text) {
        Map<String, Integer> termFrequency = new HashMap<>();
        for (String term : Text.terms(text)) {
            termFrequency.merge(term, 1, Integer::sum);
        }

        float[] vector = new float[dim];
        for (Map.Entry<String, Integer> entry : termFrequency.entrySet()) {
            long h = hash(entry.getKey());
            int bucket = (int) Math.floorMod(h, dim);
            double sign = ((h >> 33) & 1L) == 0L ? 1.0 : -1.0;
            double weight = 1.0 + Math.log(entry.getValue());
            vector[bucket] += (float) (sign * weight);
        }

        double norm = 0.0;
        for (float v : vector) {
            norm += (double) v * v;
        }
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < dim; i++) {
                vector[i] = (float) (vector[i] / norm);
            }
        }
        return vector;
    }

    /** Cosine similarity. Both vectors are expected to be L2 normalised. */
    public static double cosine(float[] a, float[] b) {
        int n = Math.min(a.length, b.length);
        double sum = 0.0;
        for (int i = 0; i < n; i++) {
            sum += (double) a[i] * b[i];
        }
        return sum;
    }

    private static long hash(String term) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] digest = md5.digest(term.getBytes(StandardCharsets.UTF_8));
            long value = 0L;
            for (int i = 0; i < 8; i++) {
                value = (value << 8) | (digest[i] & 0xffL);
            }
            return value;
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("MD5 unavailable", e);
        }
    }

    /** Renders a vector as a pgvector literal: {@code [0.1,-0.2,...]}. */
    public static String toPgVector(float[] vector) {
        StringBuilder sb = new StringBuilder(vector.length * 8);
        sb.append('[');
        for (int i = 0; i < vector.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(vector[i]);
        }
        return sb.append(']').toString();
    }

    /** Parses a pgvector literal back into a float array. */
    public static float[] fromPgVector(String literal) {
        String body = literal.trim();
        if (body.startsWith("[")) {
            body = body.substring(1);
        }
        if (body.endsWith("]")) {
            body = body.substring(0, body.length() - 1);
        }
        if (body.isBlank()) {
            return new float[0];
        }
        String[] parts = body.split(",");
        float[] out = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Float.parseFloat(parts[i].trim());
        }
        return out;
    }

    /** Convenience for tests and tooling. */
    public List<Float> embedBoxed(String text) {
        float[] raw = embed(text);
        List<Float> out = new java.util.ArrayList<>(raw.length);
        for (float v : raw) {
            out.add(v);
        }
        return out;
    }
}
