package _9_human_in_the_loop;

import dev.langchain4j.agentic.AgenticServices;
import dev.langchain4j.agentic.UntypedAgent;
import dev.langchain4j.agentic.workflow.HumanInTheLoop;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.service.AiServices;
import util.ChatModelProvider;
import util.log.CustomLogging;
import util.log.LogLevels;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Map;
import java.util.Scanner;

public class _9b_HumanInTheLoop_Chatbot_With_Memory {

    static {
        CustomLogging.setLevel(LogLevels.PRETTY, 300);  // 控制模型调用日志的显示量
    }

    /**
     * 本示例演示了与人在回路交互的来回循环，
     * 直到达成最终目标（退出条件），之后工作流的其余部分可以继续执行。
     * 循环持续进行直到人类确认可用时间，这由 AiService 验证。
     * 如果找不到合适时段，循环在5次迭代后结束。
     */

    private static final ChatModel CHAT_MODEL = ChatModelProvider.createChatModel();

    public static void main(String[] args) {

        // 1. 定义子 Agent
        MeetingProposer proposer = AgenticServices
                .agentBuilder(MeetingProposer.class)
                .chatModel(CHAT_MODEL)
                .chatMemoryProvider(memoryId -> MessageWindowChatMemory.withMaxMessages(15)) // 让 Agent 记住他已经提议了什么
                .outputKey("proposal")
                .build();

        // 2. 添加 AiService 来判断是否已达成决定（可以用小模型，因为任务很简单）
        DecisionsReachedService decisionService = AiServices.create(DecisionsReachedService.class, CHAT_MODEL);

        // 2. 定义人在回路 Agent
        HumanInTheLoop humanInTheLoop = AgenticServices
                .humanInTheLoopBuilder()
                .description("向用户请求输入的 Agent")
                .outputKey("candidateAnswer") // 与提议者的输入变量名匹配
                .responseProvider(scope -> {
                    System.out.println(scope.readState("request"));
                    System.out.print("> ");
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));
                        return reader.readLine();
                    } catch (IOException e) {
                        throw new RuntimeException("读取输入失败", e);
                    }
                })
                .async(true) // 无需在等待用户输入时阻塞整个程序
                .build();

        // 3. 构建循环
        // 这里我们只希望每次循环检查一次退出条件，而不是每次 Agent 调用后都检查，
        // 所以我们将两个 Agent 打包成序列，然后作为一个 Agent 交给循环
        UntypedAgent agentSequence = AgenticServices
                .sequenceBuilder()
                .subAgents(proposer, humanInTheLoop)
                .output(agenticScope -> Map.of(
                        "proposal", agenticScope.readState("proposal"),
                        "candidateAnswer", agenticScope.readState("candidateAnswer")
                ))
                .outputKey("proposalAndAnswer")
                // 此输出包含最后的日期提议 + 候选人的回答，应该足够让后续 Agent 安排会议（或放弃）
                .build();

        UntypedAgent schedulingLoop = AgenticServices
                .loopBuilder()
                .subAgents(agentSequence)
                .exitCondition(scope -> {
                    System.out.println("--- 检查退出条件 ---");
                    String response = (String) scope.readState("candidateAnswer");
                    String proposal = (String) scope.readState("proposal");
                    return response != null && decisionService.isDecisionReached(proposal, response);
                })
                .outputKey("proposalAndAnswer")
                .maxIterations(5)
                .build();

        // 4. 运行会议安排循环
        Map<String, Object> input = Map.of("meetingTopic", "现场访问",
                "candidateAnswer", "你好", // 此变量需要预先存在于 AgenticScope 中，因为 MeetingProposer 将其作为输入
                "memoryId", "user-1234"); // 如果不放 memoryId，提议 Agent 将不会记住他已提议的内容

        var lastProposalAndAnswer = schedulingLoop.invoke(input);

        System.out.println("=== 结果：最后的提议和回答 ===");
        System.out.println(lastProposalAndAnswer);
    }
}