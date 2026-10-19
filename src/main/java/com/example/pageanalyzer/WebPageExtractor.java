package com.example.pageanalyzer;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
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
        doc.select("script,style,noscript,svg,canvas,iframe,nav,footer,header,form,button,aside").remove();
        String title = doc.title().isBlank() ? uri.getHost() : doc.title();
        Element content = doc.selectFirst("main,article,[role=main]");
        if (content == null) content = doc.body();
        if (content == null) throw new IllegalArgumentException("No readable content found at this URL.");
        StringBuilder text = new StringBuilder();
        for (Element element : content.select("h1,h2,h3,h4,p,li,blockquote,pre,tr")) {
            String line = element.tagName().equals("tr")
                    ? String.join(" | ", element.select("th,td").eachText()) : element.text();
            if (!line.isBlank()) text.append(element.tagName().startsWith("h") ? "\n## " : "\n").append(line).append('\n');
        }
        if (text.isEmpty()) text.append(content.text());
        List<String> links = doc.select("a[href]").stream().map(e -> e.absUrl("href"))
                .filter(s -> !s.isBlank()).distinct().limit(80).toList();
        return new ExtractedContent(title, text.toString().trim(), links, uri.toString());
    }
}
