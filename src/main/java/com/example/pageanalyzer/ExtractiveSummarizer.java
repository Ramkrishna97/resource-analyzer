package com.example.pageanalyzer;

import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/** Offline extractive summary that keeps source headings and paragraph boundaries. */
@Service
public class ExtractiveSummarizer implements SummarizationService {
    private static final Set<String> STOP = Set.of("the","and","for","that","with","this","from","are","was","were","have","has","had","will","would","could","should","into","about","there","their","then","than","them","they","you","your","our","out","not","but","can","all","any","its","who","what","when","where","which","while","also","been","being","such","each","more","most","some","only","other","over","under","between","through","very","just","because","does","did","how","why","use","using","used","per","one","two","may","might","must","shall","a","an","of","to","in","on","at","by","as","is","it","or","be","if","we","he","she","i","do","no","so","up","down");
    private static final Pattern SENTENCES = Pattern.compile("(?<=[.!?])\\s+(?=[\\p{Lu}0-9])");
    private record Unit(int position, int block, String heading, String text, boolean list, boolean table, double score) { }

    @Override public String name() { return "Offline extractive summary"; }

    @Override public String summarize(String source, SummaryLevel level, String ignored) {
        if (source == null || source.isBlank()) return "No readable text was found in this source.";
        boolean preserveImages = "PRESERVE_IMAGES".equals(ignored);
        String[] blocks = source.trim().split("\\n\\s*\\n+");
        List<Unit> units = new ArrayList<>();
        String heading = "";
        int position = 0;
        for (int block = 0; block < blocks.length; block++) {
            String content = blocks[block].trim();
            if (content.isBlank()) continue;
            if (content.matches("\\[\\[IMAGE:\\d+]]")) {
                if (preserveImages) units.add(new Unit(position++, block, heading, content, false, false, 0));
                continue;
            }
            if (content.matches("(?s)^#{1,6}\\s+.+")) { heading = content.replaceFirst("^#{1,6}\\s+", ""); continue; }
            boolean table = content.startsWith("|");
            boolean list = content.matches("(?s)^[-*•]\\s+.+");
            String clean = list ? content.replaceFirst("^[-*•]\\s+", "") : content;
            if (table) units.add(new Unit(position++, block, heading, clean, false, true, 0));
            else {
                int lineNumber = 0;
                for (String line : clean.split("\\n+")) {
                    for (String sentence : SENTENCES.split(line.trim())) {
                        if (sentence.isBlank()) continue;
                        boolean enumeration = countWords(sentence) > 70 && sentence.chars().filter(ch -> ch == ';').count() >= 3;
                        for (String part : enumeration ? sentence.split(";\\s*") : new String[] { sentence })
                            if (!part.isBlank()) units.add(new Unit(position++, block * 10000 + lineNumber, heading, part.trim(), list || enumeration, false, 0));
                    }
                    lineNumber++;
                }
            }
        }
        if (units.isEmpty()) return source.trim();
        Map<String, Integer> frequency = new HashMap<>();
        for (Unit unit : units) if (!unit.table() && !unit.text().startsWith("[[IMAGE:")) for (String word : words(unit.text()))
            if (!STOP.contains(word)) frequency.merge(word, 1, Integer::sum);
        List<Unit> scored = new ArrayList<>();
        for (Unit unit : units) {
            List<String> tokens = unit.text().startsWith("[[IMAGE:") ? List.of() : words(unit.text());
            double score = tokens.stream().filter(w -> !STOP.contains(w)).mapToInt(w -> frequency.getOrDefault(w, 0)).sum()
                    / Math.sqrt(Math.max(1, tokens.size()));
            if (unit.position() < 3) score *= 1.2;
            if (!unit.heading().isBlank()) score *= 1.08;
            if (unit.table()) score *= level == SummaryLevel.LOW ? .5 : .05;
            if (tokens.size() < 5 && !unit.table()) score *= .6;
            scored.add(new Unit(unit.position(), unit.block(), unit.heading(), unit.text(), unit.list(), unit.table(), score));
        }
        int targetWords = Math.max(25, (int) Math.ceil(countWords(source) * level.targetRatio()));
        Set<Integer> selected = new HashSet<>(); int selectedWords = 0;
        for (Unit unit : scored.stream().sorted(Comparator.comparingDouble(Unit::score).reversed()).toList()) {
            if (unit.text().startsWith("[[IMAGE:")) continue;
            int words = countWords(unit.text());
            if (selectedWords + words > targetWords && !selected.isEmpty()) continue;
            if (unit.table() && level != SummaryLevel.LOW) continue;
            selected.add(unit.position()); selectedWords += words;
            if (selectedWords >= targetWords) break;
        }
        if (preserveImages) for (Unit unit : scored) if (unit.text().startsWith("[[IMAGE:")) selected.add(unit.position());
        if (selected.isEmpty()) selected.add(scored.get(0).position());
        StringBuilder out = new StringBuilder(); String lastHeading = null;
        int lastBlock = -1; boolean lastWasList = false;
        for (Unit unit : scored) {
            if (!selected.contains(unit.position())) continue;
            if (!unit.heading().equals(lastHeading)) {
                if (out.length() > 0 && !out.toString().endsWith("\n\n")) out.append("\n\n");
                if (!unit.heading().isBlank()) out.append("## ").append(unit.heading()).append("\n\n");
                lastHeading = unit.heading(); lastBlock = -1;
            }
            if (unit.text().startsWith("[[IMAGE:")) {
                if (out.length() > 0 && !out.toString().endsWith("\n\n")) out.append("\n\n");
                out.append(unit.text()).append("\n\n");
            }
            else if (unit.table()) {
                if (out.length() > 0 && !out.toString().endsWith("\n\n")) out.append("\n\n");
                out.append(unit.text()).append("\n\n");
            }
            else if (unit.list()) {
                if (out.length() > 0 && !out.toString().endsWith("\n")) out.append("\n\n");
                out.append("- ").append(unit.text()).append("\n");
            }
            else if (unit.block() == lastBlock && !lastWasList) out.append(" ").append(unit.text());
            else {
                if (out.length() > 0 && !out.toString().endsWith("\n\n")) out.append("\n\n");
                out.append(unit.text());
            }
            lastBlock = unit.block(); lastWasList = unit.list();
        }
        return out.toString().trim();
    }
    private int countWords(String value) { return value.isBlank() ? 0 : value.trim().split("\\s+").length; }
    private List<String> words(String value) { return Pattern.compile("[^\\p{L}\\p{N}]+")
            .splitAsStream(value.toLowerCase(Locale.ROOT)).filter(w -> w.length() > 2).toList(); }
}
