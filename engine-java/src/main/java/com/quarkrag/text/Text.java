package com.quarkrag.text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/** Shared, dependency-free tokenisation helpers. */
public final class Text {

    private static final Set<String> STOPWORDS = new HashSet<>(Arrays.asList(
            "the", "a", "an", "and", "or", "but", "of", "to", "in", "on", "for", "with",
            "is", "are", "was", "were", "be", "been", "it", "this", "that", "these",
            "those", "as", "at", "by", "from", "we", "you", "they", "i", "he", "she",
            "his", "her", "its", "our", "your", "their", "will", "can", "do", "does",
            "if", "then", "so", "not", "no", "yes", "have", "has", "had", "how", "what",
            "when", "where", "which", "who", "into", "out", "up", "down", "over", "my",
            "me", "us", "them", "about", "there", "here"));

    private static final Pattern WORD = Pattern.compile("[a-z0-9]+");
    private static final Pattern MARKUP = Pattern.compile("[#*_`>\\[\\]()|~]");

    private Text() {
    }

    /** Removes common markdown punctuation so cohesion is measured on prose. */
    public static String clean(String text) {
        return MARKUP.matcher(text == null ? "" : text).replaceAll(" ");
    }

    /** Splits into trimmed, non-empty sentences on terminal punctuation. */
    public static List<String> sentences(String text) {
        List<String> out = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        for (char c : text.toCharArray()) {
            if (c == '\n') {
                current.append(' ');
                continue;
            }
            current.append(c);
            if (c == '.' || c == '!' || c == '?') {
                String s = current.toString().trim();
                if (!s.isEmpty()) {
                    out.add(s);
                }
                current.setLength(0);
            }
        }
        String tail = current.toString().trim();
        if (!tail.isEmpty()) {
            out.add(tail);
        }
        return out;
    }

    /** Content terms: lowercased, stopwords removed, lightly stemmed. */
    public static List<String> terms(String text) {
        List<String> out = new ArrayList<>();
        var matcher = WORD.matcher(text == null ? "" : text.toLowerCase());
        while (matcher.find()) {
            String w = matcher.group();
            if (w.length() >= 2 && !STOPWORDS.contains(w)) {
                out.add(stem(w));
            }
        }
        return out;
    }

    /**
     * Strips a trailing plural "s".
     *
     * <p>Crude on purpose — a full Porter stemmer is overkill here — but it
     * matters more than it looks. Without it "cats"/"cat" and
     * "databases"/"database" are unrelated tokens, so cohesion between two
     * sentences about the same subject reads as zero and the chunker splits
     * mid-topic. Words ending in "ss" are left alone so "access" does not
     * become "acces".
     */
    static String stem(String word) {
        if (word.length() > 3 && word.endsWith("s") && !word.endsWith("ss")) {
            return word.substring(0, word.length() - 1);
        }
        return word;
    }

    public static Set<String> termSet(String text) {
        return new HashSet<>(terms(text));
    }

    public static int wordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        return text.trim().split("\\s+").length;
    }
}
