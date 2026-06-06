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
    private static final String BYAI_MODEL = "glm-5";  // 中转站可用模型

    // ====== 硅基流动配置 ======
    private static final String SILICONFLOW_BASE_URL = "https://api.siliconflow.cn/v1";
    private static final String SILICONFLOW_MODEL = "Qwen/Qwen3-8B";  // 8B参数，更快更稳定

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
                String siliconflowKey = System.getenv("SILICONFLOW_API_KEY");
                if (siliconflowKey == null || siliconflowKey.isEmpty()) {
                    siliconflowKey = ""; // fallback，IDEA环境变量没配时使用
                }
                return OpenAiChatModel.builder()
                        .baseUrl(SILICONFLOW_BASE_URL)
                        .apiKey(siliconflowKey)
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
                String byaiKey = System.getenv("BYAI_API_KEY");
                if (byaiKey == null || byaiKey.isEmpty()) {
                    byaiKey = "sk-CDeMw9RlCLg9LUSVCczw6qBrg4oJRilTI85CwRcpYwHQYth3"; // fallback
                }
                return OpenAiChatModel.builder()
                        .baseUrl(BYAI_BASE_URL)
                        .apiKey(byaiKey)
                        .modelName(BYAI_MODEL)
                        .logRequests(enableLogging)
                        .logResponses(enableLogging)
                        .build();
        }
    }
}