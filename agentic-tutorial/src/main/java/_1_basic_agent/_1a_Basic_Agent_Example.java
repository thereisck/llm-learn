package _1_basic_agent;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.model.chat.ChatModel;
import util.ChatModelProvider;
import util.StringLoader;
import util.log.CustomLogging;
import util.log.LogLevels;

import java.io.IOException;

public class _1a_Basic_Agent_Example {

    /**
     * 本示例演示如何实现一个基础 Agent 来展示语法。
     * 注意：Agent 只有与其他 Agent 组合使用才有意义，后续步骤会展示这一点。
     * 单个 Agent 的场景，直接用 AiService 更合适。
     *
     * 这个基础 Agent 将用户的人生故事转化为一份完整、规范的简历。
     * 注意运行可能需要一些时间，因为生成的简历内容较长，模型需要更多处理时间。
     */

    // 设置日志级别
    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 300);  // 控制模型调用日志的显示量
    }

    // 1. 定义驱动 Agent 的模型
    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel("SILICONFLOW");

    public static void main(String[] args) throws IOException {

        // 2. 在 agent_interfaces/CvGenerator.java 中定义 Agent 行为

        // 3. 使用 AgenticServices 创建 Agent
        CvGenerator cvGenerator = AgenticServices
                .agentBuilder(CvGenerator.class)
                .chatModel(CHAT_MODEL)
                .outputKey("masterCv") // 可选：定义输出对象的键名
                .build();

        // 4. 从 resources/documents/user_life_story.txt 加载文本文件
        String lifeStory = StringLoader.loadFromResource("/documents/user_life_story.txt");

        // 5. 调用 Agent 生成简历
        String cv = cvGenerator.generateCv(lifeStory);

        // 6. 打印生成的简历
        System.out.println("=== CV ===");
        System.out.println(cv);

        // 在示例 1b 中，我们将构建相同 Agent 但使用结构化输出

    }
}