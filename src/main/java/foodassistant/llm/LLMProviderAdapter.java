package foodassistant.llm;

import foodassistant.model.FoodWords;

import java.util.List;

/** Presents the vendor client as the engine the rest of the app calls. */
public final class LLMProviderAdapter implements LLMEngine {
    private final ExternalLLMClient client;

    public LLMProviderAdapter(ExternalLLMClient client) {
        this.client = client;
    }

    @Override
    public String generateResponse(String prompt) {
        return client.sendRequest(prompt);
    }

    @Override
    public String reason(String context) {
        return generateResponse(context);
    }

    @Override
    public List<String> generateTags(String text) {
        return FoodWords.mentioned(text == null ? "" : text);
    }
}
