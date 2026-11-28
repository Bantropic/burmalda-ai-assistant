package burmalda.llm;

import java.util.List;

public record LlmRequest(String model, List<LlmMessage> messages, boolean stream) {
    public LlmRequest {
        messages = List.copyOf(messages);
    }
}
