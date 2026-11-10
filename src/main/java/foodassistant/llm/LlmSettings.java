package foodassistant.llm;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Which model to build, read from supabase.local. */
public final class LlmSettings {
    private final String provider;
    private final String apiKey;
    private final String model;

    private LlmSettings(String provider, String apiKey, String model) {
        this.provider = provider;
        this.apiKey = apiKey;
        this.model = model;
    }

    public static LlmSettings fromFile(Path file) {
        String provider = "";
        String apiKey = "";
        String model = "gpt-4o-mini";
        if (file != null && Files.exists(file)) {
            try {
                for (String line : Files.readString(file).split("\\R")) {
                    int split = line.indexOf('=');
                    if (split <= 0 || line.startsWith("#")) {
                        continue;
                    }
                    String name = line.substring(0, split).trim();
                    String value = line.substring(split + 1).trim();
                    switch (name) {
                        case "LLM_PROVIDER" -> provider = value;
                        case "OPENAI_API_KEY" -> apiKey = value;
                        case "OPENAI_MODEL" -> {
                            if (!value.isBlank()) {
                                model = value;
                            }
                        }
                        default -> {
                        }
                    }
                }
            } catch (IOException ignored) {
                apiKey = "";
            }
        }
        return new LlmSettings(provider, apiKey, model);
    }

    public boolean useOpenAi() {
        if ("local".equalsIgnoreCase(provider)) {
            return false;
        }
        return !apiKey.isBlank();
    }

    public String apiKey() {
        return apiKey;
    }

    public String model() {
        return model;
    }
}
