package burmalda.llm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import org.springframework.web.server.ResponseStatusException;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class LlmGatewayClientTest {
    private final ObjectMapper json = new ObjectMapper();
    private HttpServer server;
    private ExecutorService executor;
    private String baseUrl;
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> auth = new AtomicReference<>();
    private final AtomicReference<String> method = new AtomicReference<>();
    private final AtomicReference<String> contentType = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String response = """
            {"id":"ignored","choices":[{"index":0,"finish_reason":"stop",
              "message":{"role":"assistant","content":"Привет!","reasoning_content":"ignored"}}]}
            """;
    private volatile boolean block;
    private final CountDownLatch release = new CountDownLatch(1);

    @BeforeEach void start() throws Exception {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        executor = Executors.newCachedThreadPool();
        server.setExecutor(executor);
        server.createContext("/v1/chat/completions", exchange -> {
            try {
                body.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                auth.set(exchange.getRequestHeaders().getFirst("Authorization"));
                contentType.set(exchange.getRequestHeaders().getFirst("Content-Type"));
                method.set(exchange.getRequestMethod());
                if (block) release.await(5, TimeUnit.SECONDS);
                byte[] bytes = response.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().set("Content-Type", "application/json");
                exchange.sendResponseHeaders(status, bytes.length);
                exchange.getResponseBody().write(bytes);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
    }

    @AfterEach void stop() {
        release.countDown();
        server.stop(0);
        executor.shutdownNow();
    }

    private LlmGatewayClient client() {
        return new LlmGatewayClient(json, baseUrl, "test-key", 1);
    }

    private LlmRequest request() {
        return new LlmRequestBuilder("burmalda").addMessage("user", "Привет").build();
    }

    @Test void serializesRequestAndReadsText() throws Exception {
        assertEquals("Привет!", client().complete(request()).choices().get(0).message().content());
        assertEquals(json.readTree("""
                {"model":"burmalda","messages":[{"role":"user","content":"Привет"}],"stream":false}
                """), json.readTree(body.get()));
        assertEquals("Bearer test-key", auth.get());
        assertEquals("POST", method.get());
        assertEquals("application/json", contentType.get());
    }

    @Test void builderProducesIndependentSnapshots() {
        var builder = new LlmRequestBuilder("burmalda").addMessage("user", "One");
        var first = builder.build();
        builder.addMessage("user", "Two");
        assertEquals(1, first.messages().size());
        assertEquals(2, builder.build().messages().size());
        assertThrows(UnsupportedOperationException.class, () -> first.messages().clear());
    }

    @Test void upstreamErrorDoesNotExposeBody() {
        status = 401;
        response = "sensitive provider diagnostics";
        var error = assertThrows(ResponseStatusException.class, () -> client().complete(request()));
        assertEquals(502, error.getStatusCode().value());
        assertFalse(error.getReason().contains(response));
    }

    @Test void rejectsMalformedOrIncompleteAnswers() {
        for (String invalid : new String[]{"not json", "null", "{}", "{\"choices\":[]}",
                "{\"choices\":[null]}",
                "{\"choices\":[{\"message\":{\"role\":\"assistant\",\"content\":null}}]}",
                "{\"choices\":[{\"finish_reason\":\"length\",\"message\":{\"role\":\"assistant\",\"content\":\"partial\"}}]}"}) {
            response = invalid;
            var error = assertThrows(ResponseStatusException.class, () -> client().complete(request()));
            assertEquals(502, error.getStatusCode().value());
        }
    }

    @Test void mapsTimeout() {
        block = true;
        var error = assertThrows(ResponseStatusException.class, () -> client().complete(request()));
        assertEquals(504, error.getStatusCode().value());
    }

    @Test void missingKeyDoesNotSendRequest() {
        var client = new LlmGatewayClient(json, baseUrl, "", 1);
        var error = assertThrows(ResponseStatusException.class, () -> client.complete(request()));
        assertEquals(503, error.getStatusCode().value());
        assertNull(body.get());
    }
}
