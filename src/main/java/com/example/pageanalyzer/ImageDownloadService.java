package com.example.pageanalyzer;

import org.springframework.stereotype.Service;

import java.net.InetAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Service
public class ImageDownloadService {
    public record Download(byte[] bytes, String contentType) { }

    public Download download(String value) throws Exception {
        URI uri = URI.create(value);
        if (!java.util.List.of("http", "https").contains(uri.getScheme()) || uri.getHost() == null)
            throw new IllegalArgumentException("Only HTTP or HTTPS images can be exported.");
        for (InetAddress address : InetAddress.getAllByName(uri.getHost()))
            if (address.isAnyLocalAddress() || address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isMulticastAddress())
                throw new IllegalArgumentException("Private network image addresses cannot be exported.");
        HttpRequest request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(15))
                .header("User-Agent", "Briefly/1.0").GET().build();
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<java.io.InputStream> response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
        try (var input = response.body()) {
            if (response.statusCode() != 200) throw new IllegalArgumentException("Image download returned HTTP " + response.statusCode() + ".");
            String type = response.headers().firstValue("Content-Type").orElse("").split(";", 2)[0].trim();
            if (!type.startsWith("image/")) throw new IllegalArgumentException("The image URL did not return an image.");
            byte[] data = input.readNBytes(12_000_001);
            if (data.length > 12_000_000) throw new IllegalArgumentException("An image exceeds the 12 MB export limit.");
            return new Download(data, type);
        }
    }
}
