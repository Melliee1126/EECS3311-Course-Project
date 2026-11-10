package foodassistant.llm;

import java.util.List;
import java.util.Map;

/** The language model the agent asks to write. */
public interface LLMEngine {
    String generateResponse(String prompt);

    String reason(String context);

    String complete(String system, List<Map<String, String>> messages);

    List<String> generateTags(String text);
}
