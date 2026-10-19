package com.example.pageanalyzer;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@Component
public class WebPageExtractor implements ContentExtractor<String> {
    @Override
    public ExtractedContent extract(String url) throws Exception {
        URI uri;
        try { uri = URI.create(url == null ? "" : url.trim()); }
        catch (IllegalArgumentException ex) { throw new IllegalArgumentException("Enter a valid http or https URL."); }
        if (!List.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null)
            throw new IllegalArgumentException("Enter a valid http or https URL.");
        Document doc = Jsoup.connect(uri.toString()).userAgent("PageAnalyzer/1.0 (document summarizer)")
                .timeout(15000).followRedirects(true).get();
        doc.select("script,style,noscript,svg,canvas,iframe,nav,footer,header,form,button,aside,[aria-hidden=true],.mw-editsection,.references,.reflist,.navbox,.catlinks,.printfooter,.mw-collapsible").remove();
        String title = doc.title().isBlank() ? uri.getHost() : doc.title();
        Element content = doc.selectFirst("main,article,[role=main]");
        if (content == null) content = doc.body();
        if (content == null) throw new IllegalArgumentException("No readable content found at this URL.");
        Map<String, ImageAsset> images = new LinkedHashMap<>();
        for (Element image : content.select("img")) {
            String imageUrl = image.absUrl("src");
            if (!imageUrl.startsWith("http")) imageUrl = image.absUrl("data-src");
            if (!imageUrl.startsWith("http")) continue;
            Element figure = image.closest("figure");
            Element caption = figure == null ? null : figure.selectFirst("figcaption");
            images.putIfAbsent(imageUrl, new ImageAsset(imageUrl, image.attr("alt").trim(),
                    image.attr("title").trim(), caption == null ? "" : caption.text().trim()));
            if (images.size() >= 80) break;
        }
        StringBuilder text = new StringBuilder();
        appendStructured(content, text, new ExtractionState(new ArrayList<>(images.keySet())));
        if (text.isEmpty()) text.append(content.text());
        List<String> links = doc.select("a[href]").stream().map(e -> e.absUrl("href"))
                .filter(s -> !s.isBlank()).distinct().limit(80).toList();
        return new ExtractedContent(title, text.toString().trim(), links, List.copyOf(images.values()), uri.toString());
    }

    private static class ExtractionState {
        int skippedHeadingLevel;
        final List<String> imageUrls;
        ExtractionState(List<String> imageUrls) { this.imageUrls = imageUrls; }
    }

    private void appendStructured(Element root, StringBuilder out, ExtractionState state) {
        for (Element child : root.children()) {
            String tag = child.normalName();
            if (tag.matches("h[1-6]")) {
                int level = Integer.parseInt(tag.substring(1));
                if (state.skippedHeadingLevel > 0 && level <= state.skippedHeadingLevel) state.skippedHeadingLevel = 0;
                if (child.text().matches("(?i)^(references|external links|further reading|notes|see also|citations|bibliography)(\\s*\\[.*)?$")) {
                    state.skippedHeadingLevel = level;
                    continue;
                }
                if (state.skippedHeadingLevel == 0) appendBlock(out, "#".repeat(level) + " " + child.text());
            } else if (state.skippedHeadingLevel > 0) {
                if (child.childrenSize() > 0) appendStructured(child, out, state);
            } else if (tag.equals("table")) {
                appendTable(child, out);
            } else if (tag.equals("ul") || tag.equals("ol")) {
                for (Element item : child.children()) if (item.normalName().equals("li"))
                    appendInlineWithImages(item, out, state, "- ");
            } else if (tag.equals("p") || tag.equals("blockquote") || tag.equals("pre")) {
                appendInlineWithImages(child, out, state, "");
            } else if (tag.equals("img")) {
                appendImage(child, out, state);
            } else if (child.childrenSize() > 0) {
                appendStructured(child, out, state);
            } else if (!child.text().isBlank()) {
                appendBlock(out, child.text());
            }
        }
    }

    private void appendInlineWithImages(Element element, StringBuilder out, ExtractionState state, String prefix) {
        StringBuilder segment = new StringBuilder(prefix);
        appendInlineNodes(element, segment, out, state);
        appendBlock(out, segment.toString());
    }

    private void appendInlineNodes(Element element, StringBuilder segment, StringBuilder out, ExtractionState state) {
        for (Node node : element.childNodes()) {
            if (node instanceof TextNode text) segment.append(text.text()).append(' ');
            else if (node instanceof Element child) {
                if (child.normalName().equals("img")) {
                    appendBlock(out, segment.toString()); segment.setLength(0);
                    appendImage(child, out, state);
                } else appendInlineNodes(child, segment, out, state);
            }
        }
    }

    private void appendImage(Element image, StringBuilder out, ExtractionState state) {
        String url = image.absUrl("src");
        if (!url.startsWith("http")) url = image.absUrl("data-src");
        int index = state.imageUrls.indexOf(url);
        if (index >= 0) out.append("[[IMAGE:").append(index).append("]]\n\n");
    }

    private void appendTable(Element table, StringBuilder out) {
        List<Element> rows = table.select("tr");
        if (rows.isEmpty()) return;
        int columns = rows.stream().mapToInt(r -> r.select("th,td").size()).max().orElse(0);
        if (columns == 0) return;
        List<String> headers = rows.get(0).select("th,td").eachText();
        boolean hasHeader = rows.get(0).selectFirst("th") != null;
        if (!hasHeader) headers = java.util.stream.IntStream.rangeClosed(1, columns).mapToObj(i -> "Column " + i).toList();
        out.append("| ").append(String.join(" | ", headers)).append(" |\n");
        out.append("| ").append(java.util.Collections.nCopies(headers.size(), "---").stream().collect(java.util.stream.Collectors.joining(" | "))).append(" |\n");
        for (int i = hasHeader ? 1 : 0; i < rows.size(); i++) {
            List<String> cells = new java.util.ArrayList<>(rows.get(i).select("th,td").eachText());
            while (cells.size() < headers.size()) cells.add("");
            out.append("| ").append(String.join(" | ", cells)).append(" |\n");
        }
        out.append('\n');
    }

    private void appendBlock(StringBuilder out, String text) {
        String clean = text.replaceAll("\\s+", " ").trim();
        if (!clean.isEmpty()) out.append(clean).append("\n\n");
    }
}
