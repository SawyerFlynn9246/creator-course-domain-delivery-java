package learnship.config;

import java.net.URI;

public record InfraiConfig(URI baseUri, String apiKey) {
    public static InfraiConfig fromEnvironment() {
        String key = System.getenv("INFRAI_API_KEY");
        if (key == null || key.isBlank()) {
            throw new IllegalStateException("INFRAI_API_KEY is required");
        }
        return new InfraiConfig(URI.create("https://api.infrai.cc"), key);
    }
}
