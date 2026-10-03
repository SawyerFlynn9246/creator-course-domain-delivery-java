package learnship.email;

import learnship.config.InfraiConfig;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class InfraiEmailClient implements EmailGateway {
    private static final String DOMAIN_VERIFY = "/v1/email/domain/verify";
    private static final String DOMAIN_GET = "/v1/email/domain/get/";
    private static final String EMAIL_SEND = "/v1/email/send";
    private final HttpClient http;
    private final InfraiConfig config;

    public InfraiEmailClient(InfraiConfig config) {
        this(config, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
    }

    InfraiEmailClient(InfraiConfig config, HttpClient http) {
        this.config = config;
        this.http = http;
    }

    @Override
    public String verifyDomain(String domain, String requestId) {
        String data = request("POST", DOMAIN_VERIFY, "{\"domain\":\"" + json(domain) + "\"}", requestId);
        return requiredString(object(data, "verification"), "status");
    }

    @Override
    public String domainStatus(String domain) {
        String path = DOMAIN_GET + URLEncoder.encode(domain, StandardCharsets.UTF_8);
        String data = request("GET", path, null, null);
        return requiredString(object(data, "verification"), "status");
    }

    @Override
    public String send(String to, String subject, String html, String requestId) {
        String body = "{\"to\":\"" + json(to) + "\",\"subject\":\"" + json(subject)
                + "\",\"html\":\"" + json(html) + "\"}";
        String data = request("POST", EMAIL_SEND, body, requestId);
        return requiredString(data, "message_id");
    }

    private String request(String method, String path, String body, String requestId) {
        long delayMillis = 250;
        for (int attempt = 0; attempt < 4; attempt++) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(config.baseUri().resolve(path))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + config.apiKey())
                    .header("Accept", "application/json");
            if (requestId != null) builder.header("Idempotency-Key", requestId);
            if (body == null) {
                builder.method(method, HttpRequest.BodyPublishers.noBody());
            } else {
                builder.header("Content-Type", "application/json")
                        .method(method, HttpRequest.BodyPublishers.ofString(body));
            }
            HttpResponse<String> response = send(builder.build());
            Envelope envelope = decodeEnvelope(response.body(), response.statusCode());
            if (response.statusCode() == 429) {
                sleep(retryDelay(response, delayMillis));
                delayMillis *= 2;
                continue;
            }
            if (!envelope.ok()) {
                throw new InfraiException(envelope.errorCode(), envelope.errorDetail(), response.statusCode());
            }
            if (response.statusCode() >= 500) {
                throw new InfraiException("TRANSPORT_ERROR", "Infrai request could not be completed", response.statusCode());
            }
            return envelope.data();
        }
        throw new InfraiException("RATE_LIMITED", "Retry window exhausted", 429);
    }

    private HttpResponse<String> send(HttpRequest request) {
        try {
            return http.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException e) {
            throw new InfraiException("TRANSPORT_ERROR", e.getMessage(), 503);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new InfraiException("INTERRUPTED", "Request interrupted", 503);
        }
    }

    private static Envelope decodeEnvelope(String json, int status) {
        boolean ok = Pattern.compile("\\\"ok\\\"\\s*:\\s*true").matcher(json).find();
        String data = object(json, "data");
        if (ok) return new Envelope(true, data, "", "");
        String error = object(json, "error");
        String code = optionalString(error, "code", "REQUEST_REJECTED");
        String detail = optionalString(error, "message", "Request rejected with HTTP " + status);
        return new Envelope(false, data, code, detail);
    }

    private static String object(String json, String field) {
        Matcher start = Pattern.compile("\\\"" + Pattern.quote(field) + "\\\"\\s*:\\s*\\{").matcher(json);
        if (!start.find()) return "{}";
        int begin = start.end() - 1;
        int depth = 0;
        boolean quoted = false;
        for (int i = begin; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '"' && (i == 0 || json.charAt(i - 1) != '\\')) quoted = !quoted;
            if (!quoted && c == '{') depth++;
            if (!quoted && c == '}' && --depth == 0) return json.substring(begin, i + 1);
        }
        throw new IllegalArgumentException("Malformed JSON object: " + field);
    }

    private static String requiredString(String json, String field) {
        String value = optionalString(json, field, null);
        if (value == null) throw new IllegalArgumentException("Missing response field: " + field);
        return value;
    }

    private static String optionalString(String json, String field, String fallback) {
        Matcher matcher = Pattern.compile("\\\"" + Pattern.quote(field) + "\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\"").matcher(json);
        return matcher.find() ? matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\") : fallback;
    }

    private static String json(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }

    private static long retryDelay(HttpResponse<?> response, long fallback) {
        return response.headers().firstValue("Retry-After")
                .map(value -> { try { return Long.parseLong(value) * 1000; } catch (NumberFormatException e) { return fallback; } })
                .orElse(fallback);
    }

    private static void sleep(long millis) {
        try { Thread.sleep(millis); }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new InfraiException("INTERRUPTED", "Backoff interrupted", 503); }
    }

    private record Envelope(boolean ok, String data, String errorCode, String errorDetail) {}
}
