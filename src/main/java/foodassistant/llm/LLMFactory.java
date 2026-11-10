package foodassistant.llm;

import java.nio.file.Path;

/** Builds the language model the configuration asks for. */
public abstract class LLMFactory {
    public abstract LLMEngine createLLM();

    public static LLMFactory fromFile(Path file) {
        LlmSettings settings = LlmSettings.fromFile(file);
        if (settings.useOpenAi()) {
            return new OpenAILLMFactory(settings);
        }
        return new LocalLLMFactory();
    }
}
