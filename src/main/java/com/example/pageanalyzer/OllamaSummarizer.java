package com.example.pageanalyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;

@Service
public class OllamaSummarizer {
    private final ObjectMapper mapper = new ObjectMapper();
    private final String baseUrl;
    private final String defaultModel;
    public OllamaSummarizer(@Value("${app.ollama.base-url}") String baseUrl,
                            @Value("${app.ollama.model}") String defaultModel) {
        this.baseUrl = baseUrl; this.defaultModel = defaultModel;
    }
    public String defaultModel() { return defaultModel; }
    public String summarize(String text, SummaryLevel level, String requestedModel) throws Exception {
        String model = requestedModel == null || requestedModel.isBlank() ? defaultModel : requestedModel.trim();
        int max = level == SummaryLevel.LOW ? 1400 : level == SummaryLevel.MEDIUM ? 800 : 400;
        String prompt = "Summarize the source below. " + level.guidance() + " Preserve names, numbers, dates, caveats, and section structure. Do not invent facts. Use readable headings and bullet points. Aim for at most " + max + " words.\n\nSOURCE:\n" + text;
        String body = mapper.writeValueAsString(java.util.Map.of("model", model, "prompt", prompt, "stream", false,
                "options", java.util.Map.of("num_predict", max + 100)));
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + "/api/generate"))
                .timeout(Duration.ofMinutes(5)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build()
                .send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IllegalStateException("Ollama returned HTTP " + response.statusCode() + ".");
        JsonNode json = mapper.readTree(response.body());
        if (json.hasNonNull("error")) throw new IllegalStateException(json.get("error").asText());
        return json.path("response").asText();
    }
}
