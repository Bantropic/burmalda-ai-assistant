package burmalda.service;

import burmalda.llm.LlmGatewayClient;
import burmalda.llm.LlmRequestBuilder;
import burmalda.model.Session;
import burmalda.model.Message;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.springframework.http.HttpStatus.*;

@Service
public class SessionService {
    private final Map<String, Chat> chats = new LinkedHashMap<>();
    private final LlmGatewayClient gateway;
    private final String model;
    private final String systemPrompt;

    public SessionService(LlmGatewayClient gateway, @Value("${llm.model}") String model) throws IOException {
        this.gateway = gateway;
        this.model = model;
        this.systemPrompt = new ClassPathResource("prompts/AGENTS.md")
                .getContentAsString(StandardCharsets.UTF_8).strip();
        if (systemPrompt.isBlank()) throw new IllegalStateException("System prompt is required");
    }

    public synchronized List<Session> getSessions() {
        return chats.values().stream().map(chat -> chat.session).toList();
    }

    public synchronized Session createSession() {
        var session = new Session(UUID.randomUUID().toString(), "Чат " + (chats.size() + 1));
        chats.put(session.id(), new Chat(session));
        return session;
    }

    public List<Message> getMessages(String id) {
        var chat = findChat(id);
        synchronized (chat.messages) {
            return List.copyOf(chat.messages);
        }
    }

    public Message sendMessage(String id, String content) {
        var chat = findChat(id);
        if (!chat.busy.compareAndSet(false, true)) {
            throw new ResponseStatusException(CONFLICT, "Agent is already running in this session");
        }
        try {
            var userMessage = Message.create("user", content);
            var builder = new LlmRequestBuilder(model).addMessage("system", systemPrompt);
            synchronized (chat.messages) {
                for (var message : chat.messages) {
                    builder.addMessage(message.role(), message.content());
                }
            }
            var request = builder.addMessage("user", content).build();
            var response = gateway.complete(request);
            var answer = Message.create("assistant", response.choices().get(0).message().content());
            synchronized (chat.messages) {
                chat.messages.addAll(List.of(userMessage, answer));
            }
            return answer;
        } finally {
            chat.busy.set(false);
        }
    }

    private synchronized Chat findChat(String id) {
        var chat = chats.get(id);
        if (chat == null) throw new ResponseStatusException(NOT_FOUND, "Session not found");
        return chat;
    }

    private static class Chat {
        final Session session;
        final List<Message> messages = new ArrayList<>();
        final AtomicBoolean busy = new AtomicBoolean();

        Chat(Session session) {
            this.session = session;
        }
    }
}
