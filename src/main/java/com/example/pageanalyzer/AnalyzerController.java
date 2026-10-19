package com.example.pageanalyzer;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;

@RestController
@RequestMapping("/api")
public class AnalyzerController {
    private final DocumentExtractor documents;
    private final WebPageExtractor webpages;
    private final ExtractiveSummarizer extractive;
    private final OllamaSummarizer ollama;
    public AnalyzerController(DocumentExtractor documents, WebPageExtractor webpages,
                              ExtractiveSummarizer extractive, OllamaSummarizer ollama) {
        this.documents = documents; this.webpages = webpages; this.extractive = extractive; this.ollama = ollama;
    }
    @PostMapping(value="/analyze/file", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public SummaryResult file(@RequestParam("file") MultipartFile file,
                              @RequestParam(defaultValue="MEDIUM") String level,
                              @RequestParam(defaultValue="offline") String engine,
                              @RequestParam(required=false) String model) {
        try { return analyze(documents.extract(file), level, engine, model); }
        catch (Exception ex) { throw error(ex); }
    }
    @PostMapping("/analyze/url")
    public SummaryResult url(@RequestBody Map<String,String> body) {
        try { return analyze(webpages.extract(body.get("url")), body.get("level"), body.get("engine"), body.get("model")); }
        catch (Exception ex) { throw error(ex); }
    }
    @PostMapping(value="/analyze/batch", consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<SummaryResult> batch(@RequestParam("manifest") String manifest,
                                    @RequestParam Map<String, MultipartFile> files) {
        try {
            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            var items = mapper.readTree(manifest);
            if (!items.isArray() || items.isEmpty() || items.size() > 20)
                throw new IllegalArgumentException("Add between 1 and 20 sources to combine.");
            List<SummaryResult> results = new ArrayList<>();
            for (var item : items) {
                String type = item.path("type").asText();
                String level = item.path("level").asText("MEDIUM");
                String engine = item.path("engine").asText("offline");
                String model = item.path("model").asText("");
                ExtractedContent extracted;
                if (type.equals("url")) extracted = webpages.extract(item.path("value").asText());
                else {
                    String key = item.path("key").asText();
                    MultipartFile file = files.get(key);
                    if (file == null) throw new IllegalArgumentException("A selected file is missing from the combined request.");
                    extracted = documents.extract(file);
                }
                results.add(analyze(extracted, level, engine, model));
            }
            return results;
        } catch (Exception ex) { throw error(ex); }
    }
    @GetMapping("/status")
    public Map<String,Object> status() {
        try {
            var req = java.net.http.HttpRequest.newBuilder(java.net.URI.create("http://localhost:11434/api/tags"))
                    .timeout(java.time.Duration.ofSeconds(2)).GET().build();
            var res = java.net.http.HttpClient.newHttpClient().send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
            var json = new com.fasterxml.jackson.databind.ObjectMapper().readTree(res.body());
            List<String> models = new java.util.ArrayList<>(); json.path("models").forEach(m -> models.add(m.path("name").asText()));
            return Map.of("ollamaAvailable", res.statusCode() == 200, "models", models, "defaultModel", ollama.defaultModel());
        } catch (Exception ex) { return Map.of("ollamaAvailable", false, "models", List.of(), "defaultModel", ollama.defaultModel()); }
    }
    private SummaryResult analyze(ExtractedContent content, String levelText, String engine, String model) throws Exception {
        if (content.text() == null || content.text().isBlank()) throw new IllegalArgumentException("No readable text was found in this source.");
        if (content.text().length() > 500_000) throw new IllegalArgumentException("This source is too large (maximum 500,000 extracted characters).");
        SummaryLevel level = SummaryLevel.from(levelText); String result; String used;
        if ("ollama".equalsIgnoreCase(engine)) { result = ollama.summarize(content.text(), level, model); used = "Ollama · " + (model == null || model.isBlank() ? ollama.defaultModel() : model); }
        else { result = extractive.summarize(content.text(), level, null); used = extractive.name(); }
        int originalWords = content.text().trim().split("\\s+").length;
        return new SummaryResult(content.title(), content.source(), level.name(), result, content.text(), content.links(),
                originalWords, result.trim().isEmpty() ? 0 : result.trim().split("\\s+").length, used);
    }
    private ResponseStatusException error(Exception ex) {
        if (ex instanceof ResponseStatusException response) return response;
        String message = ex.getMessage() == null ? "Could not process this source." : ex.getMessage();
        HttpStatus status = ex instanceof IllegalArgumentException ? HttpStatus.BAD_REQUEST : HttpStatus.BAD_GATEWAY;
        return new ResponseStatusException(status, message, ex);
    }
}
