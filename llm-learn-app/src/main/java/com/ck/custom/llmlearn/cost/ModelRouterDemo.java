package com.ck.custom.llmlearn.cost;

/**
 * @author changkong
 * @date 2026/6/21 10:35
 **/

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Week7 Day4 - Step1: 多模型切换策略 ModelRouter
 *
 * 核心思路：
 * - 简单任务（分类、提取、翻译）→ 小模型（快+便宜）
 * - 复杂任务（推理、生成、代码审查）→ 大模型（慢+贵但效果好）
 * - 路由依据：任务类型标签 or prompt复杂度
 *
 * 运行方式：直接跑main方法
 * 观察重点：
 * 1. 同一个问题，不同模型的响应质量和速度差异
 * 2. Token消耗和成本对比
 *
 * @author changkong
 * @date 2026/6/21
 */
public class ModelRouterDemo {
    // ========== 模型配置 ==========
    /**
     * 小模型：快速+便宜，适合简单任务
     * SiliconFlow上的免费/低价模型
     */
    private static final String SMALL_MODEL = "Qwen/Qwen3-8B";
    /**
     * 大模型：强能力，适合复杂推理任务
     * 你一直在用的GLM-5.1
     */
    private static final String LARGE_MODEL = "Pro/zai-org/GLM-5.1";

    /**
     * SiliconFlow API（你项目里已配置的）
     * 从环境变量读key，跟项目其他Demo一致
     */
    private static final String BASE_URL = "https://api.siliconflow.cn/v1";
    private static final String API_KEY = "sk-kemgxaacaqdehipgoywavavmpaiazkocfjyixcvsnslmeycv";

    // ========== 任务类型枚举 ==========
    enum TaskType {
        // 简单任务：分类、提取、翻译、格式转换
        CLASSIFICATION,
        EXTRACTION,
        TRANSLATION,
        // 复杂任务：推理、代码生成、创意写作
        REASONING,
        CODE_GENERATION,
        CREATIVE_WRITING
    }

    // ========== 路由规则 ==========
    /**
     * 根据任务类型决定用哪个模型
     * 这是策略模式的核心——你肯定写过类似的
     */
    static String routeModel(TaskType taskType) {
        return switch (taskType) {
            // 简单任务 → 小模型（省钱+快）
            case CLASSIFICATION, EXTRACTION, TRANSLATION -> SMALL_MODEL;
            // 复杂任务 → 大模型（质量优先）
            case REASONING, CODE_GENERATION, CREATIVE_WRITING -> LARGE_MODEL;
        };
    }

    /**
     * 根据任务类型创建对应的ChatModel
     */
    static ChatModel createModel(String modelName) {
        return OpenAiChatModel.builder()
                .baseUrl(BASE_URL)
                .apiKey(API_KEY)
                .modelName(modelName)
                .timeout(Duration.ofSeconds(120))
                .logRequests(true)
                .logResponses(false)  // 响应太长，先关掉
                .build();
    }

    // ========== 成本统计 ==========
    static class CostTracker {
        private final Map<String, Integer> modelCallCount = new HashMap<>();
        private final Map<String, Long> modelTotalLatency = new HashMap<>();
        void record(String model, long latencyMs) {
            modelCallCount.merge(model, 1, Integer::sum);
            modelTotalLatency.merge(model, latencyMs, Long::sum);
        }
        void printReport() {
            System.out.println("\n========== 成本统计报告 ==========");
            modelCallCount.forEach((model, count) -> {
                long avgLatency = modelTotalLatency.get(model) / count;
                System.out.printf("模型: %-25s | 调用次数: %d | 平均耗时: %dms%n",
                        model, count, avgLatency);
            });
            System.out.println("==================================\n");
        }
    }

    // ========== 测试用例 ==========
    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("Week7 Day4 Step1: 多模型切换策略 ModelRouter");
        System.out.println("=".repeat(60));
        if (API_KEY.isEmpty()) {
            System.err.println("❌ 请先设置环境变量 SILICONFLOW_API_KEY");
            System.err.println("   export SILICONFLOW_API_KEY=sk-xxx");
            return;
        }
        CostTracker costTracker = new CostTracker();
        // === 测试1：简单任务——情感分类（应该路由到小模型）===
        System.out.println("\n--- 测试1：情感分类（简单任务）---");
        runTask(TaskType.CLASSIFICATION,
                "请对以下评论进行情感分类，只返回：正面/负面/中性\n\n评论：这个手机充电很快，但电池不耐用。",
                costTracker);
        // === 测试2：简单任务——信息提取（应该路由到小模型）===
        System.out.println("\n--- 测试2：信息提取（简单任务）---");
        runTask(TaskType.EXTRACTION,
                "从以下文本中提取人名和职位，格式：姓名-职位\n\n" +
                        "张三是阿里巴巴的资深架构师，李四是字节跳动的技术专家。",
                costTracker);
        // === 测试3：复杂任务——代码生成（应该路由到大模型）===
        System.out.println("\n--- 测试3：代码生成（复杂任务）---");
        runTask(TaskType.CODE_GENERATION,
                "用Java写一个线程安全的LRU缓存，要求：\n" +
                        "1. 使用LinkedHashMap实现\n" +
                        "2. 容量可配置\n" +
                        "3. 过期时间支持\n" +
                        "给出完整代码+简要注释。",
                costTracker);
        // === 测试4：复杂任务——推理（应该路由到大模型）===
        System.out.println("\n--- 测试4：逻辑推理（复杂任务）---");
        runTask(TaskType.REASONING,
                "一个房间里有3个开关控制隔壁房间的3盏灯。\n" +
                        "你只能在隔壁房间待一次。如何确定每个开关控制哪盏灯？\n" +
                        "请给出详细推理过程。",
                costTracker);
        // === 成本报告 ===
        costTracker.printReport();
        // === 对比实验：用错误的路由会怎样？===
        System.out.println("--- 对比实验：用小模型做复杂任务（故意路由错误）---");
        System.out.println("（看看小模型做不了复杂任务的样子）\n");
        String complexPrompt = "用Java写一个支持读写锁的并发哈希表，要求支持put/get/contains/size操作，" +
                "读写不互斥但写写互斥，给出完整代码。";
        String wrongModel = SMALL_MODEL;  // 故意用小模型
        System.out.println("使用模型: " + wrongModel + "（应为: " + LARGE_MODEL + "）");
        long start = System.currentTimeMillis();
        ChatModel wrongChatModel = createModel(wrongModel);
        String wrongResponse = wrongChatModel.chat(complexPrompt);
        long latency = System.currentTimeMillis() - start;
        System.out.println("响应内容（前500字）: " +
                wrongResponse.substring(0, Math.min(500, wrongResponse.length())));
        System.out.println("... (总长度: " + wrongResponse.length() + " 字符)");
        System.out.println("耗时: " + latency + "ms");
        System.out.println("\n💡 观察：小模型做复杂任务，可能代码不完整、逻辑有误、或者直接放弃。");
        System.out.println("\n" + "=".repeat(60));
        System.out.println("Step1 完成！核心收获：");
        System.out.println("1. ModelRouter = 策略模式，按任务类型选模型");
        System.out.println("2. 简单任务用小模型省钱省时间");
        System.out.println("3. 复杂任务必须大模型，小模型会翻车");
        System.out.println("4. 成本统计让每次调用的花费可视化");
        System.out.println("=".repeat(60));
    }

    /**
     * 执行单个任务：路由→创建模型→调用→记录成本
     */
    private static void runTask(TaskType taskType, String prompt, CostTracker tracker) {
        String modelName = routeModel(taskType);
        System.out.println("路由结果: " + taskType + " → " + modelName);
        long start = System.currentTimeMillis();
        ChatModel chatModel = createModel(modelName);
        String response = chatModel.chat(prompt);
        long latency = System.currentTimeMillis() - start;
        System.out.println("响应内容: " +
                (response.length() > 300 ? response.substring(0, 300) + "..." : response));
        System.out.println("耗时: " + latency + "ms");
        System.out.println("响应长度: " + response.length() + " 字符");
        tracker.record(modelName, latency);
    }

}
