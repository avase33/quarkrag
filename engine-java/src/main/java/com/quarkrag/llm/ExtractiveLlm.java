package com.quarkrag.llm;

import com.quarkrag.store.VectorStore;
import com.quarkrag.text.Text;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * The offline synthesizer: ranks every sentence in the retrieved chunks by term
 * overlap with the question and joins the best few.
 *
 * <p>Grounded by construction — an answer can only contain text that exists in
 * the corpus, so faithfulness is 1.0 by definition. The trade-off is fluency:
 * it cannot paraphrase or reason across chunks the way a real model would.
 * That is the honest boundary between this and {@code quarkrag.llm=openai}.
 */
public class ExtractiveLlm implements LlmClient {

    private static final int MAX_SENTENCES = 3;

    @Override
    public String answer(String question, List<VectorStore.Hit> contexts) {
        Set<String> questionTerms = Text.termSet(question);
        if (contexts.isEmpty() || questionTerms.isEmpty()) {
            return "I don't have information on that in the indexed documents.";
        }

        record Scored(double score, String sentence) {
        }
        List<Scored> scored = new ArrayList<>();

        for (VectorStore.Hit hit : contexts) {
            for (String sentence : Text.sentences(hit.text())) {
                Set<String> sentenceTerms = Text.termSet(sentence);
                if (sentenceTerms.isEmpty()) {
                    continue;
                }
                Set<String> overlap = new LinkedHashSet<>(questionTerms);
                overlap.retainAll(sentenceTerms);
                if (!overlap.isEmpty()) {
                    // normalise by question size so long sentences do not win by volume
                    scored.add(new Scored((double) overlap.size() / questionTerms.size(), sentence));
                }
            }
        }

        if (scored.isEmpty()) {
            return "I don't have information on that in the indexed documents.";
        }

        scored.sort(Comparator.comparingDouble(Scored::score).reversed());

        Set<String> seen = new LinkedHashSet<>();
        StringBuilder answer = new StringBuilder();
        for (Scored s : scored) {
            String key = s.sentence().toLowerCase();
            if (!seen.add(key)) {
                continue;
            }
            if (!answer.isEmpty()) {
                answer.append(' ');
            }
            answer.append(s.sentence());
            if (seen.size() >= MAX_SENTENCES) {
                break;
            }
        }
        return answer.toString();
    }

    @Override
    public String id() {
        return "extractive";
    }
}
