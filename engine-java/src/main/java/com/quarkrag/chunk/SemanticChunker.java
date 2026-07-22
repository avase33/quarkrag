package com.quarkrag.chunk;

import com.quarkrag.text.Text;
import jakarta.enterprise.context.ApplicationScoped;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Splits a document at <b>topic shifts</b> rather than at arbitrary token
 * counts — a TextTiling-style approach, written from scratch.
 *
 * <p>Cohesion between adjacent sentences is the Otsuka-Ochiai coefficient over
 * their content-term sets:
 *
 * <pre>
 *   c[i] = |terms(s_i) ∩ terms(s_i+1)| / sqrt(|terms(s_i)| · |terms(s_i+1)|)
 * </pre>
 *
 * <p>A boundary is placed after sentence {@code i} when {@code c[i]} is a local
 * minimum <i>and</i> dips below the document's mean cohesion, or when the
 * running chunk would exceed {@code maxWords}. Chunks therefore keep their
 * topical meaning, which is what makes retrieval precise: an embedding of a
 * chunk that spans two unrelated subjects is an average of both and matches
 * neither well.
 */
@ApplicationScoped
public class SemanticChunker {

    public record Chunk(int index, String text, int sentences, double cohesion) {
    }

    public List<Chunk> chunk(String rawText, int maxWords) {
        String cleaned = Text.clean(rawText);
        List<String> sentences = Text.sentences(cleaned);
        List<Chunk> chunks = new ArrayList<>();

        if (sentences.isEmpty()) {
            return chunks;
        }
        if (sentences.size() == 1) {
            chunks.add(new Chunk(0, sentences.get(0), 1, 1.0));
            return chunks;
        }

        List<Set<String>> termSets = new ArrayList<>(sentences.size());
        for (String s : sentences) {
            termSets.add(Text.termSet(s));
        }

        double[] cohesion = new double[sentences.size() - 1];
        for (int i = 0; i < cohesion.length; i++) {
            cohesion[i] = cohesion(termSets.get(i), termSets.get(i + 1));
        }
        double mean = 0.0;
        for (double c : cohesion) {
            mean += c;
        }
        mean /= cohesion.length;

        double variance = 0.0;
        for (double c : cohesion) {
            variance += (c - mean) * (c - mean);
        }
        double std = Math.sqrt(variance / cohesion.length);

        int start = 0;
        int index = 0;
        for (int i = 0; i < sentences.size(); i++) {
            boolean last = i == sentences.size() - 1;
            boolean overCap = i > start && words(sentences, start, i) >= maxWords;
            boolean shift = !last && isBoundary(cohesion, i, mean, std);

            if (overCap || shift || last) {
                chunks.add(build(sentences, cohesion, start, i, index++));
                start = i + 1;
            }
        }
        return chunks;
    }

    /**
     * A significant valley in cohesion.
     *
     * <p>Three conditions, all necessary:
     *
     * <ol>
     *   <li><b>Interior only.</b> Both neighbours must exist. A dip at either
     *       end has no opposite shoulder, so there is no evidence it is a
     *       valley rather than a trend — and with only two measurements one of
     *       them is always "below average", which would split every short
     *       document.
     *   <li><b>Below the document mean.</b> The dip is weak relative to how
     *       tightly this particular document hangs together.
     *   <li><b>Deep enough.</b> The drop from both shoulders, summed, must
     *       exceed one standard deviation of cohesion. This is the TextTiling
     *       depth score, and it is what stops a flat run of equally-low values
     *       from producing a boundary at every step.
     * </ol>
     */
    private static boolean isBoundary(double[] cohesion, int i, double mean, double std) {
        if (i <= 0 || i >= cohesion.length - 1) {
            return false;
        }
        double left = cohesion[i - 1];
        double right = cohesion[i + 1];
        if (cohesion[i] >= mean || cohesion[i] > left || cohesion[i] > right) {
            return false;
        }
        double depth = (left - cohesion[i]) + (right - cohesion[i]);
        return depth >= std;
    }

    private static Chunk build(List<String> sentences, double[] cohesion,
                               int start, int end, int index) {
        StringBuilder sb = new StringBuilder();
        for (int j = start; j <= end; j++) {
            if (j > start) {
                sb.append(' ');
            }
            sb.append(sentences.get(j));
        }
        double inner = 1.0;
        if (end > start) {
            double sum = 0.0;
            for (int j = start; j < end; j++) {
                sum += cohesion[j];
            }
            inner = sum / (end - start);
        }
        return new Chunk(index, sb.toString(), end - start + 1, round3(inner));
    }

    private static int words(List<String> sentences, int start, int end) {
        int total = 0;
        for (int j = start; j <= end; j++) {
            total += Text.wordCount(sentences.get(j));
        }
        return total;
    }

    /** |A ∩ B| / sqrt(|A|·|B|) */
    static double cohesion(Set<String> a, Set<String> b) {
        if (a.isEmpty() || b.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(a);
        intersection.retainAll(b);
        return intersection.size() / Math.sqrt((double) a.size() * b.size());
    }

    private static double round3(double v) {
        return Math.round(v * 1000.0) / 1000.0;
    }
}
