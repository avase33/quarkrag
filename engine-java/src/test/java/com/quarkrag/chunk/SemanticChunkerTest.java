package com.quarkrag.chunk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SemanticChunkerTest {

    private final SemanticChunker chunker = new SemanticChunker();

    @Test
    void splitsAtATopicShift() {
        String text = """
                Cats are wonderful pets. Cats purr and cats sleep a lot.
                A kitten is a young cat.
                Databases store structured data. A database index speeds up queries.
                Query planners optimize database access.""";

        List<SemanticChunker.Chunk> chunks = chunker.chunk(text, 200);

        assertTrue(chunks.size() >= 2, "expected a topic boundary, got " + chunks.size());
        assertTrue(chunks.get(0).text().toLowerCase().contains("cat"));
        assertTrue(chunks.stream().anyMatch(c -> c.text().toLowerCase().contains("database")));
    }

    @Test
    void keepsOneCoherentParagraphTogether() {
        String text = "The deploy pipeline builds the artifact. "
                + "The pipeline then runs the deploy tests. "
                + "A green pipeline promotes the artifact to staging.";

        List<SemanticChunker.Chunk> chunks = chunker.chunk(text, 200);

        assertEquals(1, chunks.size(), "high-cohesion text should not be split");
        assertEquals(3, chunks.get(0).sentences());
    }

    @Test
    void respectsMaxWords() {
        String sentence = "alpha beta gamma delta epsilon zeta. ";
        List<SemanticChunker.Chunk> chunks = chunker.chunk(sentence.repeat(20), 20);

        assertTrue(chunks.size() > 1, "should split on the word cap");
        for (SemanticChunker.Chunk chunk : chunks) {
            int words = chunk.text().trim().split("\\s+").length;
            // the cap can overshoot by at most the sentence that crossed it
            assertTrue(words <= 20 + 8, "chunk had " + words + " words");
        }
    }

    @Test
    void handlesEmptyAndSingleSentence() {
        assertTrue(chunker.chunk("", 100).isEmpty());
        List<SemanticChunker.Chunk> one = chunker.chunk("Just one sentence here.", 100);
        assertEquals(1, one.size());
        assertEquals(1, one.get(0).sentences());
    }

    @Test
    void stripsMarkdownBeforeChunking() {
        List<SemanticChunker.Chunk> chunks = chunker.chunk("# Title\n\nSome *bold* `code` text.", 100);
        assertFalse(chunks.isEmpty());
        String text = chunks.get(0).text();
        assertFalse(text.contains("#"));
        assertFalse(text.contains("*"));
        assertTrue(text.contains("Title"));
    }
}
