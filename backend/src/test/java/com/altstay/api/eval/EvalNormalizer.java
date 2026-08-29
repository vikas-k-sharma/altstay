package com.altstay.api.eval;

import java.util.Locale;

public final class EvalNormalizer {

    private EvalNormalizer() {
    }

    public static String normalize(String text) {
        if (text == null) {
            return "";
        }
        String normalized = text.toLowerCase(Locale.ROOT);
        // Replace punctuation marks that might interfere with token matching
        normalized = normalized.replaceAll("[\\r\\n]+", " ");
        // Currency normalizations
        normalized = normalized.replace("₹", "rs ");
        normalized = normalized.replace("inr", "rs");
        normalized = normalized.replace("rupees", "rs");
        normalized = normalized.replace("rupee", "rs");
        // Time normalizations: e.g., "2:00 pm" -> "2 pm", "14:00" -> "2 pm"
        normalized = normalized.replaceAll("(\\d{1,2}):00\\s*(am|pm)", "$1 $2");
        normalized = normalized.replaceAll("(\\d{1,2})\\s*(am|pm)", "$1 $2");
        // Collapse multiple spaces
        normalized = normalized.replaceAll("\\s+", " ").trim();
        return normalized;
    }

    public static int wordCount(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        String cleaned = text.trim().replaceAll("\\s+", " ");
        return cleaned.split(" ").length;
    }

    public static boolean containsNormalized(String text, String substring) {
        if (text == null || substring == null) {
            return false;
        }
        String normText = normalize(text);
        String normSub = normalize(substring);
        return normText.contains(normSub);
    }
}
