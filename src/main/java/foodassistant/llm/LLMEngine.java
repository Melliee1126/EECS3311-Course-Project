package foodassistant.llm;

import java.util.List;

/** The language model the agent asks to write. */
public interface LLMEngine {
    String generateResponse(String prompt);

    String reason(String context);

    List<String> generateTags(String text);
}
