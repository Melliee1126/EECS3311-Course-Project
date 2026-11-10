package foodassistant.llm;

public final class LocalLLMFactory extends LLMFactory {
    @Override
    public LLMEngine createLLM() {
        return new LocalLLMEngine();
    }
}
