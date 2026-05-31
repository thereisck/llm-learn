package _2_sequential_workflow;

import _1_basic_agent.CvGenerator;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.model.chat.ChatModel;
import util.ChatModelProvider;
import util.StringLoader;
import util.log.CustomLogging;
import util.log.LogLevels;

import java.io.IOException;
import java.util.Map;

public class _2a_Sequential_Agent_Example {

    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 300);  // 控制模型调用日志的显示量
    }

    /**
     * 本示例演示如何实现两个 Agent：
     * - CvGenerator（接收人生故事，生成完整的主简历）
     * - CvTailor（接收主简历，根据特定指令（岗位描述、反馈等）进行定制）
     * 然后使用 sequenceBuilder 将它们按固定顺序调用，演示如何在这些 Agent 之间传递参数。
     * 组合多个 Agent 时，所有输入、中间和输出参数以及调用链都存储在 AgenticScope 中，高级场景可以访问它。
     */

    // 1. 定义驱动 Agent 的模型
    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel("OPENAI");

    public static void main(String[] args) throws IOException {

        // 2. 在本包中定义两个子 Agent：
        //      - CvGenerator.java
        //      - CvTailor.java

        // 3. 使用 AgenticServices 创建两个 Agent
        CvGenerator cvGenerator = AgenticServices
                .agentBuilder(CvGenerator.class)
                .chatModel(CHAT_MODEL)
                .outputKey("masterCv") // 如果要将此变量从 Agent 1 传递给 Agent 2，
                // 确保此输出键名与第二个 Agent 接口中的输入变量名匹配
                .build();
        CvTailor cvTailor = AgenticServices
                .agentBuilder(CvTailor.class)
                .chatModel(CHAT_MODEL) // 注意：不同 Agent 可以使用不同的模型
                .outputKey("tailoredCv") // 需要定义输出对象的键名
                // 如果在这里写 "masterCv"，原始主简历会被第二个 Agent 的输出覆盖。
                // 本场景中我们不希望这样，但某些场景下这是个有用的特性。
                .build();

        ////////////////// 无类型示例 //////////////////////

        // 4. 构建顺序工作流
        UntypedAgent tailoredCvGenerator = AgenticServices // 使用 UntypedAgent，除非你定义了结果组合 Agent，见下方
                .sequenceBuilder()
                .subAgents(cvGenerator, cvTailor) // 可以添加任意数量的 Agent，顺序很重要
                .outputKey("tailoredCv") // 这是组合 Agent 的最终输出
                // 注意：可以使用 AgenticScope 中的任意字段作为输出
                // 例如你可以输出 'masterCv' 而非 tailoredCv（虽然本场景中没意义）
                .build();

        // 4. 从 resources/documents/ 中的文本文件加载参数
        // - user_life_story.txt
        // - job_description_backend.txt
        String lifeStory = StringLoader.loadFromResource("/documents/user_life_story.txt");
        String instructions = "根据以下岗位描述定制简历。" + StringLoader.loadFromResource("/documents/job_description_backend.txt");

        // 5. 因为使用无类型 Agent，需要传入参数 Map
        Map<String, Object> arguments = Map.of(
                "lifeStory", lifeStory, // 与 CvGenerator.java 中的变量名匹配
                "instructions", instructions // 与 CvTailor.java 中的变量名匹配
        );

        // 5. 调用组合 Agent 生成定制简历
        String tailoredCv = (String) tailoredCvGenerator.invoke(arguments);

        // 6. 打印生成的简历
        System.out.println("=== 定制简历（无类型） ===");
        System.out.println((String) tailoredCv); // 可以观察到，使用不同的岗位描述文件会产生截然不同的简历

        // 在示例 2b 中，我们将构建相同顺序 Agent 但使用有类型输出，并查看 AgenticScope

    }
}