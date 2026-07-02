package com.ck.custom.llmlearn.context;

/**
 * @author changkong
 * @date 2026/6/19 16:50
 **/

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * Week7 Day3 - Step3: 摘要压缩ChatMemory演示
 *
 * 核心原理：
 * 1. 对话消息超过阈值时，不直接丢弃老消息
 * 2. 而是调用LLM把老消息压缩成一段摘要（SystemMessage）
 * 3. 摘要 + 最近几轮完整对话 → 作为上下文发送给LLM
 * 4. 对比滑动窗口：关键信息不丢失，只是被"浓缩"了
 *
 * 运行方式：直接跑main方法
 * 观察重点：
 * - 第4轮触发压缩后，摘要里有没有"张空少"和"Java后端"
 * - 第5轮问AI"我叫什么"→ 能不能答出来（滑动窗口版答不出来！）
 */
public class SummaryCompressionDemo {

    /** 摘要消息的标识前缀，用于区分普通SystemMessage */
    private static final String SUMMARY_PREFIX = "[对话摘要] ";

    private final ChatModel chatModel;
    private final int maxMessages;       // 触发压缩的消息阈值
    private final int keepRecent;        // 压缩时保留最近几条不压缩
    private final List<ChatMessage> messages = new ArrayList<>();

    public SummaryCompressionDemo(ChatModel chatModel, int maxMessages, int keepRecent) {
        this.chatModel = chatModel;
        this.maxMessages = maxMessages;
        this.keepRecent = keepRecent;
    }

    /**
     * 主对话方法：用户说一句，AI回一句
     */
    public String chat(String userMessage) {
        // 1. 添加用户消息
        messages.add(UserMessage.from(userMessage));
        // 2. 检查是否需要压缩（消息数## Step 3：摘要压缩版 — 让AI不再失忆
        if (messages.size() > maxMessages) {
            compress();
        }
        // 3. 打印当前Memory状态
        printMemoryStatus();
        // 4. 发送给LLM（此时messages里是：摘要 + 最近几轮完整对话）
        String response = chatModel.chat(messages).aiMessage().text();
        // 5. AI回复加入memory
        messages.add(AiMessage.from(response));
        return response;
    }

    /**
     * 核心：压缩逻辑
     * 把老消息喂给LLM生成摘要，用摘要替换老消息
     */
    private void compress() {
        // 2.1 分割消息：老的待压缩 + 最近的保留
        int compressCount = messages.size() - keepRecent;
        List<ChatMessage> toCompress = new ArrayList<>(messages.subList(0, compressCount));
        List<ChatMessage> toKeep = new ArrayList<>(messages.subList(compressCount, messages.size()));
        // 2.2 检查是否已有旧摘要（第一轮压缩没有，后续压缩要把旧摘要合并）
        String oldSummary = extractOldSummary();
        if (oldSummary != null) {
            // 移除旧摘要SystemMessage（它在toCompress或toKeep的头部）
            if (!toCompress.isEmpty() && toCompress.get(0) instanceof SystemMessage) {
                toCompress.remove(0);
            }
        }
        // 2.3 调用LLM生成摘要
        String summary = generateSummary(toCompress, oldSummary);
        // 2.4 重建消息列表：新摘要 + 保留的最近消息
        messages.clear();
        messages.add(SystemMessage.from(SUMMARY_PREFIX + summary));
        messages.addAll(toKeep);
        System.out.println("  ⚡ 触发压缩！将 " + toCompress.size() + " 条老消息 → 1 条摘要");
        System.out.println("  📝 摘要: " + truncate(summary, 120));
        System.out.println("  📦 压缩后Memory: 1条摘要 + " + toKeep.size() + "条近期消息 = " + messages.size() + "条");
    }

    /**
     * 调用LLM把老消息压缩成摘要
     */
    private String generateSummary(List<ChatMessage> msgs, String oldSummary) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("请将以下对话历史压缩成一段简洁的摘要。\n");
        prompt.append("要求：\n");
        prompt.append("1. 保留所有人名、职业、技术栈等关键实体\n");
        prompt.append("2. 保留用户的偏好、需求和正在做的事情\n");
        prompt.append("3. 保留重要结论\n");
        prompt.append("4. 去掉寒暄、客套话等无用信息\n");
        prompt.append("5. 200字以内，纯文本输出\n\n");
        // 如果有旧摘要，先放旧摘要（实现累积压缩）
        if (oldSummary != null) {
            prompt.append("【之前的摘要】\n").append(oldSummary).append("\n\n");
            prompt.append("【新增对话】\n");
        }
        // 把待压缩的消息转成文本
        for (ChatMessage msg : msgs) {
            if (msg instanceof SystemMessage) {
                prompt.append("系统: ").append(((SystemMessage) msg).text()).append("\n");
            } else if (msg instanceof UserMessage um) {
                prompt.append("用户: ").append(um.singleText()).append("\n");
            } else if (msg instanceof AiMessage am) {
                prompt.append("AI: ").append(am.text()).append("\n");
            }
        }
        prompt.append("\n请输出更新后的摘要（200字以内）：");
        return chatModel.chat(prompt.toString());
    }

    /**
     * 从messages头部提取已有的旧摘要（如果有）
     */
    private String extractOldSummary() {
        if (!messages.isEmpty() && messages.get(0) instanceof SystemMessage sm) {
            String text = sm.text();
            if (text.startsWith(SUMMARY_PREFIX)) {
                return text.substring(SUMMARY_PREFIX.length());
            }
        }
        return null;
    }

    /**
     * 打印当前Memory中的消息列表
     */
    private void printMemoryStatus() {
        System.out.println("【当前Memory: " + messages.size() + "条消息】");
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage msg = messages.get(i);
            String type = msg.type().toString();
            if (msg instanceof SystemMessage) type = "📝SUMMARY";
            System.out.println("  [" + i + "] " + type + ": " + truncate(getText(msg), 70));
        }
    }

    private String getText(ChatMessage msg) {
        if (msg instanceof SystemMessage sm) return sm.text();
        if (msg instanceof UserMessage um) return um.singleText();
        if (msg instanceof AiMessage am) return am.text();
        return msg.toString();
    }
    private static String truncate(String s, int max) {
        if (s == null) return "null";
        return s.length() > max ? s.substring(0, max) + "..." : s;
    }

    // ================================================================
    // 主方法：跑5轮对话，观察压缩过程
    // ================================================================
    public static void main(String[] args) {
        ChatModel chatModel = OpenAiChatModel.builder()
                .apiKey("sk-kemgxaacaqdehipgoywavavmpaiazkocfjyixcvsnslmeycv")
                .baseUrl("https://api.siliconflow.cn/v1")
                .modelName("deepseek-ai/DeepSeek-V4-Pro")
                .timeout(Duration.ofSeconds(120))  // 摘要生成需要额外调LLM，超时要加长
                .build();
        // maxMessages=6：超过6条触发压缩
        // keepRecent=4：压缩时保留最近4条（2轮完整对话）
        // 对比滑动窗口：滑动窗口窗口=4会丢信息，摘要压缩窗口=6且不丢！
        SummaryCompressionDemo chat = new SummaryCompressionDemo(chatModel, 6, 4);
        // 同样的5轮对话，对比滑动窗口版的结果
        String[] turns = {
                "我叫张空少，是一个Java后端开发者，今年转行做大模型应用",
                "我之前学过RAG系统搭建，用的是LangChain4j",
                "我今天在学习上下文管理",
                "你能记住我叫什么吗？我叫什么名字？",
                "我是做什么工作的？我之前学了什么？"
        };
        for (int i = 0; i < turns.length; i++) {
            System.out.println("========== 第" + (i + 1) + "轮 ==========");
            System.out.println("用户: " + turns[i]);
            String response = chat.chat(turns[i]);
            System.out.println("AI: " + truncate(response, 200));
            System.out.println();
        }
        // 最终对比总结
        System.out.println("========== 对比总结 ==========");
        System.out.println("┌─────────────┬──────────────────┬──────────────────┐");
        System.out.println("│ 策略        │ 第5轮知道名字？  │ Token消耗        │");
        System.out.println("├─────────────┼──────────────────┼──────────────────┤");
        System.out.println("│ 滑动窗口    │ ❌ 已丢弃         │ 低（固定4条）     │");
        System.out.println("│ 摘要压缩    │ ✅ 在摘要里保留   │ 中（摘要+近对话） │");
        System.out.println("└─────────────┴──────────────────┴──────────────────┘");
    }
}
