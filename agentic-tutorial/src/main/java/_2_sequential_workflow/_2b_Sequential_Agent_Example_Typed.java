package _2_sequential_workflow;

import _1_basic_agent.CvGenerator;
import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.scope.AgenticScope;
import dev.langchain4j.agentic.scope.ResultWithAgenticScope;
import dev.langchain4j.model.chat.ChatModel;
import util.AgenticScopePrinter;
import util.ChatModelProvider;
import util.StringLoader;
import util.log.CustomLogging;
import util.log.LogLevels;

import java.io.IOException;
import java.util.Map;

public class _2b_Sequential_Agent_Example_Typed {

    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 150);  // 控制模型调用日志的显示量
    }

    /**
     * 我们将实现与 2a 相同的顺序工作流，但这次：
     * - 使用有类型接口作为组合 Agent（SequenceCvGenerator）
     * - 可以用方法参数代替 .invoke(argsMap) 来调用
     * - 以自定义方式收集输出
     * - 获取并查看 AgenticScope，用于调试或测试
     */

    // 1. 定义驱动 Agent 的模型
    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel();

    public static void main(String[] args) throws IOException {

        // 2. 在本包中定义顺序 Agent 接口：
        //      - SequenceCvGenerator.java
        // 方法签名：
        // ResultWithAgenticScope<Map<String, String>> generateTailoredCv(@V("lifeStory") String lifeStory, @V("instructions") String instructions);

        // 3. 使用 AgenticServices 创建两个子 Agent，与之前相同
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


        // 4. 从 resources/documents/ 中的文本文件加载参数
        // （这次不需要放入 Map）
        // - user_life_story.txt
        // - job_description_backend.txt
        String lifeStory = StringLoader.loadFromResource("/documents/user_life_story.txt");
        String instructions = "根据以下岗位描述定制简历。" + StringLoader.loadFromResource("/documents/job_description_backend.txt");

        // 5. 构建有类型顺序工作流，自定义输出处理
        SequenceCvGenerator sequenceCvGenerator = AgenticServices
                .sequenceBuilder(SequenceCvGenerator.class) // 这里指定有类型接口
                .subAgents(cvGenerator, cvTailor)
                .outputKey("bothCvsAndLifeStory")
                .output(agenticScope -> { // 可以自定义任意输出逻辑，这里我们收集一些内部变量
                    Map<String, String> bothCvsAndLifeStory = Map.of(
                            "lifeStory", agenticScope.readState("lifeStory", ""),
                            "masterCv", agenticScope.readState("masterCv", ""),
                            "tailoredCv", agenticScope.readState("tailoredCv", "")
                    );
                    return bothCvsAndLifeStory;
                    })
                .build();

        // 6. 调用有类型组合 Agent
        ResultWithAgenticScope<Map<String,String>> bothCvsAndScope = sequenceCvGenerator.generateTailoredCv(lifeStory, instructions);

        // 7. 提取结果和 agenticScope
        AgenticScope agenticScope = bothCvsAndScope.agenticScope();
        Map<String,String> bothCvsAndLifeStory = bothCvsAndScope.result();

        System.out.println("=== 用户信息（输入） ===");
        String userStory = bothCvsAndLifeStory.get("lifeStory");
        System.out.println(userStory.length() > 100 ? userStory.substring(0, 100) + " [truncated...]" : lifeStory);
        System.out.println("=== 主简历（有类型）（中间变量） ===");
        String masterCv = bothCvsAndLifeStory.get("masterCv");
        System.out.println(masterCv.length() > 100 ? masterCv.substring(0, 100) + " [truncated...]" : masterCv);
        System.out.println("=== 定制简历（有类型）（输出） ===");
        String tailoredCv = bothCvsAndLifeStory.get("tailoredCv");
        System.out.println(tailoredCv.length() > 100 ? tailoredCv.substring(0, 100) + " [truncated...]" : tailoredCv);

        // 无类型和有类型 Agent 的定制简历结果相同
        // （任何差异来自 LLM 的非确定性），
        // 但有类型 Agent 更优雅，且有编译时类型检查保障

        System.out.println("=== AGENTIC SCOPE ===");
        System.out.println(AgenticScopePrinter.printPretty(agenticScope, 100));
        // 这将返回如下对象（已填充）：
        // AgenticScope {
        //     memoryId = "e705028d-e90e-47df-9709-95953e84878c",
        //             state = {
        //                     bothCvsAndLifeStory = { // 输出
        //                             masterCv = "...",
        //                            lifeStory = "...",
        //                            tailoredCv = "..."
        //                     },
        //                     instructions = "...", // 输入和中间变量
        //                     tailoredCv = "...",
        //                     masterCv = "...",
        //                     lifeStory = "..."
        //             }
        // }
        System.out.println("=== 对话上下文（所有对话消息） ===");
        System.out.println(AgenticScopePrinter.printConversation(agenticScope.contextAsConversation(), 100));

    }
}