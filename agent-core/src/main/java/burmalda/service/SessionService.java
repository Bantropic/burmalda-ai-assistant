package burmalda.service;

import burmalda.model.Session;
import burmalda.model.Message;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import static org.springframework.http.HttpStatus.*;

@Service
public class SessionService {
    private final Map<String, Chat> chats = new LinkedHashMap<>();
    private final long delayMillis;

    public SessionService(@Value("${mock.delay-seconds}") double delaySeconds) {
        if (!Double.isFinite(delaySeconds)) throw new IllegalArgumentException("Delay must be finite");
        this.delayMillis = (long) (Math.max(0, delaySeconds) * 1000);
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
            Thread.sleep(delayMillis);
            if (content.strip().equals("/error")) {
                throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Simulated agent failure");
            }
            var answer = Message.create("assistant", "Тестовый ответ: ты написал «" + content + "»");
            synchronized (chat.messages) {
                chat.messages.addAll(List.of(userMessage, answer));
            }
            return answer;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(INTERNAL_SERVER_ERROR, "Agent execution interrupted");
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
