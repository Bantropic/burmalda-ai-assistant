package burmalda.llm;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

// Create a separate builder for each request; it must not be a singleton bean.
public final class LlmRequestBuilder {
    private final String model;
    private final List<LlmMessage> messages = new ArrayList<>();

    public LlmRequestBuilder(String model) {
        if (model == null || model.isBlank()) throw new IllegalArgumentException("Model is required");
        this.model = model;
    }

    public LlmRequestBuilder addMessage(String role, String content) {
        messages.add(new LlmMessage(Objects.requireNonNull(role), Objects.requireNonNull(content)));
        return this;
    }

    public LlmRequest build() {
        if (messages.isEmpty()) throw new IllegalStateException("At least one message is required");
        return new LlmRequest(model, messages, false);
    }
}
