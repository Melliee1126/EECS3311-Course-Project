package foodassistant.llm;

import java.util.List;

/** Cloud model, reached through the adapter. */
public final class OpenAILLMEngine implements LLMEngine {
    private final LLMEngine adapter;

    public OpenAILLMEngine(ExternalLLMClient client) {
        this.adapter = new LLMProviderAdapter(client);
    }

    @Override
    public String generateResponse(String prompt) {
        return adapter.generateResponse(prompt);
    }

    @Override
    public String reason(String context) {
        return adapter.reason(context);
    }

    @Override
    public List<String> generateTags(String text) {
        return adapter.generateTags(text);
    }
}
