package com.example.pageanalyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;

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
    /** The same locally installed models shown by `ollama list`. */
    public List<String> installedModels() {
        try {
            Process process = new ProcessBuilder("ollama", "list").redirectErrorStream(true).start();
            if (!process.waitFor(6, TimeUnit.SECONDS)) { process.destroyForcibly(); return List.of(); }
            if (process.exitValue() != 0) return List.of();
            record ModelSize(String name, long bytes) { }
            List<ModelSize> models = new ArrayList<>();
            for (String line : new String(process.getInputStream().readAllBytes()).split("\\R")) {
                String[] columns = line.trim().split("\\s+");
                if (columns.length >= 4 && !columns[0].equalsIgnoreCase("NAME"))
                    models.add(new ModelSize(columns[0], parseSize(columns[2], columns[3])));
            }
            return models.stream().sorted(Comparator.comparingLong(ModelSize::bytes).reversed())
                    .map(ModelSize::name).toList();
        } catch (Exception ignored) { return List.of(); }
    }
    private long parseSize(String amount, String unit) {
        try {
            long scale = switch (unit.toUpperCase(java.util.Locale.ROOT)) {
                case "KB" -> 1024L;
                case "MB" -> 1024L * 1024;
                case "GB" -> 1024L * 1024 * 1024;
                case "TB" -> 1024L * 1024 * 1024 * 1024;
                default -> 1L;
            };
            return (long) (Double.parseDouble(amount) * scale);
        } catch (NumberFormatException ex) { return 0; }
    }
    /** Releases every model currently resident in Ollama memory. */
    public int unloadAll() throws Exception {
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
        HttpRequest list = HttpRequest.newBuilder(URI.create(baseUrl + "/api/ps"))
                .timeout(Duration.ofSeconds(5)).GET().build();
        HttpResponse<String> loaded;
        try { loaded = client.send(list, HttpResponse.BodyHandlers.ofString()); }
        catch (java.net.ConnectException ex) { return 0; }
        if (loaded.statusCode() != 200) throw new IllegalStateException("Ollama could not list loaded models.");
        int count = 0;
        for (JsonNode model : mapper.readTree(loaded.body()).path("models")) {
            String name = model.path("name").asText(model.path("model").asText());
            if (name.isBlank()) continue;
            String body = mapper.writeValueAsString(java.util.Map.of("model", name, "prompt", "", "keep_alive", 0, "stream", false));
            HttpRequest stop = HttpRequest.newBuilder(URI.create(baseUrl + "/api/generate"))
                    .timeout(Duration.ofSeconds(20)).header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body)).build();
            HttpResponse<String> response = client.send(stop, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("Could not unload " + name + ".");
            count++;
        }
        return count;
    }
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
