package foodassistant.llm;

public final class OpenAILLMFactory extends LLMFactory {
    private final LlmSettings settings;

    public OpenAILLMFactory(LlmSettings settings) {
        this.settings = settings;
    }

    @Override
    public LLMEngine createLLM() {
        return new OpenAILLMEngine(new ExternalLLMClient(settings.apiKey(), settings.model()));
    }
}
