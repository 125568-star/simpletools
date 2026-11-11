package com.myapps.mydictation;

import java.util.List;
import java.util.Locale;

public final class NoteClassifier {
    private NoteClassifier() {}

    public static String folder(String transcript, String selectedKind) {
        if (transcript == null) return "Unfiled";
        String s = transcript.trim().toLowerCase(Locale.ROOT);
        String first = s.split("[.!?\n]", 2)[0];
        if (first.contains("business idea") || first.contains("startup idea")) return "Business Ideas";
        if (first.contains("shopping list") || first.startsWith("buy ") || "shopping".equals(selectedKind)) return "Shopping";
        if (first.contains("to-do") || first.contains("todo") || first.contains("task list") || "todo".equals(selectedKind)) return "To-do";
        if (first.contains("reminder") || first.startsWith("remind me")) return "Reminders";
        return "Unfiled";
    }

    public static String kind(String transcript, String chosenKind) {
        if (!"note".equals(chosenKind)) return chosenKind;
        String first = transcript == null ? "" :
            transcript.trim().toLowerCase(Locale.ROOT).split("[.!?\n]", 2)[0];
        if (first.contains("to-do item") || first.contains("todo item")) return "todo";
        if (first.contains("shopping list")) return "shopping";
        return chosenKind;
    }

    /**
     * A short usable title from the actual speech, not "New Note".
     * The caller only applies it when the user has not customised the title.
     */
    public static String title(String transcript, String kind) {
        String s = transcript == null ? "" : transcript.trim().replaceAll("\\s+", " ");
        if (s.isEmpty()) return "Untitled Note";
        String firstSentence = s.split("[.!?]", 2)[0].trim();
        if (firstSentence.isEmpty()) firstSentence = s;
        String cleaned = firstSentence.replaceFirst(
            "(?i)^(?:this is (?:a |an )?|i have (?:a |an )?)?(?:business idea|startup idea|shopping list|to-do item|todo item|a note|note|reminder)(?:\\s+(?:about|for|to))?\\s*[:,-]?\\s*",
            ""
        ).trim();
        if (cleaned.length() >= 4) firstSentence = cleaned;
        String[] words = firstSentence.split("\\s+");
        StringBuilder title = new StringBuilder();
        for (int i=0; i<Math.min(words.length, 10); i++) {
            if (title.length() + words[i].length() + (i > 0 ? 1 : 0) > 60) break;
            if (title.length() != 0) title.append(' ');
            title.append(words[i]);
        }
        return title.length() == 0 ? "Untitled Note" : title.toString();
    }

    /**
     * Reuse an existing user-named folder only when there is a unique literal
     * match in the transcript. Ambiguous cases stay in Unfiled.
     */
    public static String matchExistingFolder(String transcript, List<String> folders) {
        if (transcript == null || folders == null) return "Unfiled";
        String norm = " " + transcript.toLowerCase(Locale.ROOT)
            .replaceAll("[^\\p{L}\\p{N}]+", " ").trim() + " ";
        String hit = null;
        for (String f : folders) {
            if (f == null || f.equalsIgnoreCase("Unfiled") || f.equalsIgnoreCase("All Notes")) continue;
            String term = f.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{L}\\p{N}]+", " ").trim();
            if (!term.isEmpty() && norm.contains(" " + term + " ")) {
                if (hit != null && !hit.equalsIgnoreCase(f)) return "Unfiled";
                hit = f;
            }
        }
        return hit == null ? "Unfiled" : hit;
    }
}
