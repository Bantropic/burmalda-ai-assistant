package burmalda.llm;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import static org.springframework.http.HttpStatus.*;

@Component
public class LlmGatewayClient {
    private final ObjectMapper json;
    private final HttpClient http;
    private final URI endpoint;
    private final String apiKey;
    private final Duration timeout;

    public LlmGatewayClient(ObjectMapper json,
            @Value("${llm.base-url}") String baseUrl,
            @Value("${llm.api-key}") String apiKey,
            @Value("${llm.timeout-seconds}") long timeoutSeconds) {
        if (timeoutSeconds <= 0) throw new IllegalArgumentException("LLM timeout must be positive");
        this.json = json;
        this.endpoint = URI.create(baseUrl.replaceAll("/+$", "") + "/chat/completions");
        this.apiKey = apiKey;
        this.timeout = Duration.ofSeconds(timeoutSeconds);
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public LlmResponse complete(LlmRequest request) {
        if (apiKey.isBlank()) {
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "LLM gateway key is not configured");
        }
        try {
            var httpRequest = HttpRequest.newBuilder(endpoint)
                    .timeout(timeout)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(request)))
                    .build();
            var response = http.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() < 200 || response.statusCode() >= 300) {
                throw new ResponseStatusException(BAD_GATEWAY,
                        "LLM gateway returned HTTP " + response.statusCode());
            }
            var result = json.readValue(response.body(), LlmResponse.class);
            if (result == null || result.choices() == null || result.choices().isEmpty()
                    || result.choices().get(0) == null || result.choices().get(0).message() == null
                    || !"assistant".equals(result.choices().get(0).message().role())
                    || result.choices().get(0).message().content() == null
                    || result.choices().get(0).message().content().isBlank()
                    || !"stop".equals(result.choices().get(0).finishReason())) {
                throw new ResponseStatusException(BAD_GATEWAY, "LLM gateway did not return a complete text answer");
            }
            return result;
        } catch (HttpTimeoutException exception) {
            throw new ResponseStatusException(GATEWAY_TIMEOUT, "LLM gateway timed out");
        } catch (JsonProcessingException exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "Invalid LLM gateway JSON");
        } catch (IOException exception) {
            throw new ResponseStatusException(BAD_GATEWAY, "LLM gateway is unavailable");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(SERVICE_UNAVAILABLE, "LLM request interrupted");
        }
    }
}
