package com.example.pageanalyzer;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/** Offline fallback: ranks sentences against the document's most informative terms. */
@Service
public class ExtractiveSummarizer implements SummarizationService {
    private static final Set<String> STOP = Set.of("the","and","for","that","with","this","from","are","was","were","have","has","had","will","would","could","should","into","about","there","their","then","than","them","they","you","your","our","out","not","but","can","all","any","its","who","what","when","where","which","while","also","been","being","such","each","more","most","some","only","other","over","under","between","through","very","just","because","does","did","how","why","use","using","used","per","one","two","may","might","must","shall","a","an","of","to","in","on","at","by","as","is","it","or","be","if","we","he","she","i","do","no","so","up","down");
    @Override public String name() { return "Offline extractive summary"; }
    @Override public String summarize(String text, SummaryLevel level, String ignored) {
        if (text == null || text.isBlank()) return "No readable text was found in this source.";
        List<String> sentences = Pattern.compile("(?<=[.!?])\\s+|\\n+").splitAsStream(text.replaceAll("(?m)^\\s*#+\\s*", ""))
                .map(String::trim).filter(s -> s.split("\\s+").length >= 4).toList();
        if (sentences.size() <= 2) return text.trim();
        Map<String, Integer> frequencies = new HashMap<>();
        for (String sentence : sentences) for (String word : words(sentence)) if (!STOP.contains(word)) frequencies.merge(word, 1, Integer::sum);
        int total = sentences.stream().mapToInt(s -> s.split("\\s+").length).sum();
        int target = Math.max(30, (int)(total * level.targetRatio()));
        record Scored(int index, String sentence, double score) { }
        List<Scored> scored = new ArrayList<>();
        for (int i = 0; i < sentences.size(); i++) {
            String sentence = sentences.get(i); List<String> tokens = words(sentence);
            double score = tokens.stream().filter(w -> !STOP.contains(w)).mapToInt(w -> frequencies.getOrDefault(w, 0)).sum()
                    / Math.sqrt(Math.max(1, tokens.size()));
            if (i == 0) score *= 1.18;
            if (sentence.length() < 35) score *= .75;
            scored.add(new Scored(i, sentence, score));
        }
        List<Scored> chosen = new ArrayList<>(); int count = 0;
        for (Scored sentence : scored.stream().sorted(Comparator.comparingDouble(Scored::score).reversed()).toList()) {
            int n = sentence.sentence().split("\\s+").length;
            if (!chosen.isEmpty() && count + n > target && (level == SummaryLevel.HIGH || count >= target * .85)) continue;
            chosen.add(sentence); count += n;
            if (count >= target) break;
        }
        String result = chosen.stream().sorted(Comparator.comparingInt(Scored::index)).map(Scored::sentence).collect(Collectors.joining(" "));
        return result.isBlank() ? sentences.get(0) : result;
    }
    private List<String> words(String sentence) { return Pattern.compile("[^\\p{L}\\p{N}]+")
            .splitAsStream(sentence.toLowerCase(Locale.ROOT)).filter(w -> w.length() > 2).toList(); }
}
