package util;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import static dev.langchain4j.model.openai.OpenAiChatModelName.GPT_4_O_MINI;

/**
 * ChatModel 提供者 - 支持多种模型提供商（中转站、硅基流动、Cerebras）
 */
public class ChatModelProvider {

    // ====== 中转站配置 ======
    private static final String BYAI_BASE_URL = "https://model.indata.cc/v1";

    // ====== 硅基流动配置 ======
    private static final String SILICONFLOW_BASE_URL = "https://api.siliconflow.cn/v1";
    private static final String SILICONFLOW_MODEL = "Pro/zai-org/GLM-5.1";

    public static ChatModel createChatModel() {
        return createChatModel(true);
    }

    public static ChatModel createChatModel(boolean enableLogging) {
        return createChatModel("OPENAI", enableLogging);
    }

    public static ChatModel createChatModel(String provider) {
        return createChatModel(provider, true);
    }

    public static ChatModel createChatModel(String provider, boolean enableLogging) {
        switch (provider.toUpperCase()) {
            case "SILICONFLOW":
                return OpenAiChatModel.builder()
                        .baseUrl(SILICONFLOW_BASE_URL)
                        .modelName(SILICONFLOW_MODEL)
                        .logRequests(enableLogging)
                        .logResponses(enableLogging)
                        .build();
            case "CEREBRAS":
                return OpenAiChatModel.builder()
                        .baseUrl("https://api.cerebras.ai/v1")
                        .apiKey(System.getenv("CEREBRAS_API_KEY"))
                        .modelName("llama-4-scout-17b-16e-instruct")
                        .logRequests(enableLogging)
                        .logResponses(enableLogging)
                        .build();
            default:
                // 默认走中转站，用 BYAI_API_KEY
                return OpenAiChatModel.builder()
                        .baseUrl(BYAI_BASE_URL)
                        .apiKey(System.getenv("BYAI_API_KEY"))
                        .modelName(GPT_4_O_MINI)
                        .logRequests(enableLogging)
                        .logResponses(enableLogging)
                        .build();
        }
    }
}