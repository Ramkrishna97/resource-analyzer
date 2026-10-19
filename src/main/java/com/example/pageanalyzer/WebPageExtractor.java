package com.example.pageanalyzer;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.List;

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
        doc.select("script,style,noscript,svg,canvas,iframe,nav,footer,header,form,button,aside,[aria-hidden=true]").remove();
        String title = doc.title().isBlank() ? uri.getHost() : doc.title();
        Element content = doc.selectFirst("main,article,[role=main]");
        if (content == null) content = doc.body();
        if (content == null) throw new IllegalArgumentException("No readable content found at this URL.");
        StringBuilder text = new StringBuilder();
        appendStructured(content, text);
        if (text.isEmpty()) text.append(content.text());
        List<String> links = doc.select("a[href]").stream().map(e -> e.absUrl("href"))
                .filter(s -> !s.isBlank()).distinct().limit(80).toList();
        return new ExtractedContent(title, text.toString().trim(), links, uri.toString());
    }

    private void appendStructured(Element root, StringBuilder out) {
        for (Element child : root.children()) {
            String tag = child.normalName();
            if (tag.matches("h[1-6]")) {
                appendBlock(out, "\n" + "#".repeat(Integer.parseInt(tag.substring(1))) + " " + child.text());
            } else if (tag.equals("table")) {
                appendTable(child, out);
            } else if (tag.equals("ul") || tag.equals("ol")) {
                for (Element item : child.children()) if (item.normalName().equals("li"))
                    appendBlock(out, "- " + item.text());
            } else if (tag.equals("p") || tag.equals("blockquote") || tag.equals("pre")) {
                appendBlock(out, child.text());
            } else if (child.childrenSize() > 0) {
                appendStructured(child, out);
            } else if (!child.text().isBlank()) {
                appendBlock(out, child.text());
            }
        }
    }

    private void appendTable(Element table, StringBuilder out) {
        List<Element> rows = table.select("tr");
        if (rows.isEmpty()) return;
        int columns = rows.stream().mapToInt(r -> r.select("th,td").size()).max().orElse(0);
        if (columns == 0) return;
        List<String> headers = rows.get(0).select("th,td").eachText();
        boolean hasHeader = rows.get(0).selectFirst("th") != null;
        if (!hasHeader) headers = java.util.stream.IntStream.rangeClosed(1, columns).mapToObj(i -> "Column " + i).toList();
        appendBlock(out, "");
        appendBlock(out, "| " + String.join(" | ", headers) + " |");
        appendBlock(out, "| " + java.util.Collections.nCopies(headers.size(), "---").stream().collect(java.util.stream.Collectors.joining(" | ")) + " |");
        for (int i = hasHeader ? 1 : 0; i < rows.size(); i++) {
            List<String> cells = new java.util.ArrayList<>(rows.get(i).select("th,td").eachText());
            while (cells.size() < headers.size()) cells.add("");
            appendBlock(out, "| " + String.join(" | ", cells) + " |");
        }
    }

    private void appendBlock(StringBuilder out, String text) {
        String clean = text.replaceAll("\\s+", " ").trim();
        if (!clean.isEmpty()) out.append(clean).append("\n\n");
    }
}
