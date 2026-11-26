package burmalda.model;

import java.time.Instant;
import java.util.UUID;

public record Message(String id, String role, String content, Instant createdAt) {
    public static Message create(String role, String content) {
        return new Message(UUID.randomUUID().toString(), role, content, Instant.now());
    }
}
