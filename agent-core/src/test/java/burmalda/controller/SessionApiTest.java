package burmalda.controller;

import burmalda.service.SessionService;

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

@SpringBootTest(properties = "mock.delay-seconds=0")
@AutoConfigureMockMvc
class SessionApiTest {
    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;

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
                .andExpect(jsonPath("$.content").value("Тестовый ответ: ты написал «Привет»"))
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
        mvc.perform(post(path).contentType("application/json").content("{\"content\":\"/error\"}"))
                .andExpect(status().isInternalServerError()).andExpect(jsonPath("$.message").isString());
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
        var service = new SessionService(0.3);
        String id = service.createSession().id();
        var pool = Executors.newFixedThreadPool(2);
        var start = new CyclicBarrier(2);
        Callable<Integer> send = () -> {
            start.await(2, TimeUnit.SECONDS);
            try {
                service.sendMessage(id, "Hello");
                return 200;
            } catch (ResponseStatusException exception) {
                return exception.getStatusCode().value();
            }
        };
        try {
            var first = pool.submit(send);
            var second = pool.submit(send);
            var statuses = List.of(first.get(3, TimeUnit.SECONDS), second.get(3, TimeUnit.SECONDS));
            assertTrue(statuses.containsAll(List.of(200, 409)), statuses.toString());
            assertEquals(2, service.getMessages(id).size());
        } finally {
            pool.shutdownNow();
        }
    }
}
