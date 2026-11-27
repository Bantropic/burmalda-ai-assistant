package burmalda.controller;

import burmalda.service.SessionService;
import burmalda.llm.*;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import static org.mockito.Mockito.*;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;
import java.util.List;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class SessionApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @MockitoBean LlmGatewayClient gateway;

    private static LlmResponse answer() {
        return new LlmResponse(List.of(new LlmResponse.Choice(
                new LlmResponse.AssistantMessage("assistant", "Привет от модели"), "stop")));
    }

    @BeforeEach void setupGateway() {
        when(gateway.complete(any())).thenReturn(answer());
    }


    private String createSession() throws Exception {
        var response = mvc.perform(post("/sessions"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").isString())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(response).get("id").asText();
    }

    @Test void conversationAndIsolation() throws Exception {
        String path = "/sessions/" + createSession() + "/messages";
        String other = "/sessions/" + createSession() + "/messages";
        mvc.perform(post(path).contentType("application/json").content("{\"content\":\"Привет\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("assistant"))
                .andExpect(jsonPath("$.content").value("Привет от модели"))
                .andExpect(jsonPath("$.createdAt").isString());
        mvc.perform(get(path)).andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[1].role").value("assistant"));
        mvc.perform(get(other)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/sessions")).andExpect(status().isOk()).andExpect(jsonPath("$").isArray());
    }

    @Test void errorsAndRecovery() throws Exception {
        String path = "/sessions/" + createSession() + "/messages";
        for (String body : List.of("", "{}", "null", "[]", "{", "{\"content\":1}",
                "{\"content\":\" \"}", "{content:'hello'}", "{\"content\":\"x\"} trailing")) {
            mvc.perform(post(path).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.message").isString());
        }
        mvc.perform(get("/sessions/missing/messages")).andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Session not found"));
        when(gateway.complete(any()))
                .thenThrow(new ResponseStatusException(BAD_GATEWAY, "LLM gateway is unavailable"))
                .thenReturn(answer());
        mvc.perform(post(path).contentType("application/json").content("{\"content\":\"/error\"}"))
                .andExpect(status().isBadGateway()).andExpect(jsonPath("$.message").isString());
        mvc.perform(get(path)).andExpect(jsonPath("$.length()").value(0));
        mvc.perform(post(path).contentType("application/json").content("{\"content\":\"Retry\"}"))
                .andExpect(status().isOk());
    }

    @Test void healthAndCors() throws Exception {
        mvc.perform(get("/healthz")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
        mvc.perform(options("/sessions").header("Origin", "http://localhost:3000")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk()).andExpect(header().string("Access-Control-Allow-Origin", "*"));
    }

    @Test void concurrentMessages() throws Exception {
        var entered = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        var blockingGateway = mock(LlmGatewayClient.class);
        when(blockingGateway.complete(any())).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(3, TimeUnit.SECONDS));
            return answer();
        });
        var service = new SessionService(blockingGateway, "burmalda");
        String id = service.createSession().id();
        var pool = Executors.newSingleThreadExecutor();
        try {
            var first = pool.submit(() -> service.sendMessage(id, "Hello"));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            var error = assertThrows(ResponseStatusException.class,
                    () -> service.sendMessage(id, "Concurrent"));
            assertEquals(409, error.getStatusCode().value());
            release.countDown();
            assertEquals("Привет от модели", first.get(3, TimeUnit.SECONDS).content());
            assertEquals(2, service.getMessages(id).size());
        } finally {
            release.countDown();
            pool.shutdownNow();
        }
    }

    @Test void sendsSessionHistoryBeforeCurrentMessage() throws Exception {
        String path = "/sessions/" + createSession() + "/messages";
        for (String content : List.of("First", "Second")) {
            mvc.perform(post(path).contentType("application/json")
                    .content(json.writeValueAsString(java.util.Map.of("content", content))))
                    .andExpect(status().isOk());
        }
        var requests = org.mockito.ArgumentCaptor.forClass(LlmRequest.class);
        verify(gateway, times(2)).complete(requests.capture());
        assertEquals(List.of(new LlmMessage("user", "First")),
                requests.getAllValues().get(0).messages());
        assertEquals(List.of(
                new LlmMessage("user", "First"),
                new LlmMessage("assistant", "Привет от модели"),
                new LlmMessage("user", "Second")), requests.getValue().messages());
        assertEquals("burmalda", requests.getValue().model());
        assertFalse(requests.getValue().stream());

        String otherPath = "/sessions/" + createSession() + "/messages";
        mvc.perform(post(otherPath).contentType("application/json")
                .content("{\"content\":\"Other chat\"}"))
                .andExpect(status().isOk());
        var allRequests = org.mockito.ArgumentCaptor.forClass(LlmRequest.class);
        verify(gateway, times(3)).complete(allRequests.capture());
        assertEquals(List.of(new LlmMessage("user", "Other chat")),
                allRequests.getValue().messages());
    }
}
