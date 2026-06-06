package com.ck.custom.llmlearn.agents.smart_assistant;

import dev.langchain4j.service.AiServices;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import lombok.extern.slf4j.Slf4j;

/**
 * LangChain4j 版智能助手Agent
 *
 * 对比Python版的核心差异：
 * 1. 工具注册：Python手动写JSON Schema + append → LangChain4j @Tool注解自动发现
 * 2. Agent循环：Python手写agent_loop(500行) → LangChain4j AiServices一行build
 * 3. FC兜底：Python自己写extract_tool_calls_from_text → LangChain4j框架内置处理
 * 4. 参数容错：Python写fix_args映射 → LangChain4j框架自动校验
 * 5. 权限控制：Python写check_permission三层 → 需要自己在@Tool方法内加（这里简化为auto）
 * 6. 执行顺序Bug：Python的TOOL_SCHEMAS.append排在if__name__之后 → LangChain4j不存在此问题
 */
@Slf4j
public class SmartAssistantExample {

    // ========== AI Service接口定义（LangChain4j核心机制） ==========
    /**
     * LangChain4j的AiServices会自动：
     * 1. 把@SystemMessage作为system prompt
     * 2. 把@UserMessage作为user prompt模板
     * 3. 把绑定的Tools对象的所有@Tool方法注册为工具
     * 4. 自动处理tool_calls的循环（调用→结果→喂回LLM→直到最终回答）
     *
     * 对比Python版：你需要手写整个agent_loop来处理这些！
     */
    interface SmartAssistant {

        @SystemMessage("你是一个智能助手，可以使用工具帮助用户。你有天气查询、数据库查询、计算器、文件读取、文件写入等工具。" +
                "当用户的问题可以通过工具解决时，你必须调用工具，不要自己猜测答案。" +
                "例如：查天气必须调getWeather，不要自己编造天气信息；查数据必须调queryMysql；算数学必须调calculate。" +
                "只有工具无法解决的问题，才用自己的知识回答。")
        String chat(@UserMessage String userMessage);
    }


    public static void main(String[] args) {
        log.info("==================================================");
        log.info("Agent智能助手 - LangChain4j版（对比Python手敲版）");
        log.info("==================================================");

        // 1. 创建ChatModel（跟Python版一样，用SiliconFlow API）
        ChatModel chatModel = createChatModel();

        // 2. 创建工具实例
        SmartAssistantTools tools = new SmartAssistantTools();

        // 3. 创建AI Service（核心：一行代码搞定Agent循环+工具注册）
        //    对比Python版：你需要500行代码来做同样的事！
        SmartAssistant assistant = AiServices.builder(SmartAssistant.class)
                .chatModel(chatModel)
                .tools(tools)  // 注入工具对象，框架自动扫描@Tool注解
                .build();

        // 4. 测试（跟Python版同样的4个测试场景）

        // 测试1：纯对话
        log.info("\n[纯对话] 你好，请用一句话介绍你自己");
        String result1 = assistant.chat("你好，请用一句话介绍你自己");
        log.info("✅ 回答: {}", result1);

        // 测试2：单工具调用
        log.info("\n[单工具] 北京今天天气怎么样？");
        String result2 = assistant.chat("北京今天天气怎么样？");
        log.info("✅ 回答: {}", result2);

        // 测试3：多工具串联
        log.info("\n[串联] 上海和北京温度差是多少？");
        String result3 = assistant.chat("上海和北京温度差是多少？");
        log.info("✅ 回答: {}", result3);

        // 测试4：文件读写串联
        log.info("\n[文件串联] 把'Week5 Day7完成!'写入/tmp/summary.txt，然后读取确认");
        String result4 = assistant.chat("把'Week5 Day7完成!'写入/tmp/summary.txt，然后读取确认");
        log.info("✅ 回答: {}", result4);

        log.info("\n==================================================");
        log.info("🎉 LangChain4j版全部测试完成！");
        log.info("==================================================");
    }


    /**
     * 创建ChatModel——使用SiliconFlow API + GLM-5.1（跟Python版一致）
     *
     * 对比Python版：
     * Python: OpenAI client + base_url配置
     * LangChain4j: OpenAiChatModel.builder() + baseUrl配置
     * 两者本质一样，只是Java的builder模式比Python的字典参数更规范
     */
    private static ChatModel createChatModel() {
        String apiKey = System.getenv("SILICONFLOW_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            apiKey = ""; // fallback
        }

        return OpenAiChatModel.builder()
                .baseUrl("https://api.siliconflow.cn/v1")
                .apiKey(apiKey)
                .modelName("Pro/zai-org/GLM-5.1")
                .timeout(java.time.Duration.ofSeconds(120))
                .logRequests(true)
                .logResponses(true)
                .build();
    }
}