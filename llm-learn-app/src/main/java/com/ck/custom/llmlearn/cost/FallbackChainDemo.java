package com.ck.custom.llmlearn.cost;

/**
 * @author changkong
 * @date 2026/6/21 12:37
 **/

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;

/**
 * Week7 Day4 - Step2: Fallback 链 — 主模型挂了自动降级
 *
 * 核心思路：
 * - 配置一条模型优先级链：主模型 → 备用模型 → 兜底模型
 * - 主模型超时/报错 → 自动切到下一个 → 依次尝试直到成功
 * - 参考 OpenClaw 的 failover 机制：生产环境必须有 Plan B
 *
 * 设计模式：责任链模式（Chain of Responsibility）
 * 你在 Spring 里见过各种 Interceptor Chain，本质一样
 *
 * 运行方式：直接跑main方法
 * 观察重点：
 * 1. 主模型故意配错 → 自动 fallback 到备用模型
 * 2. 全链路都挂了 → 优雅返回兜底响应
 * 3. 每次降级都有日志，知道降到了哪一级
 *
 * @author changkong
 * @date 2026/6/21
 */
public class FallbackChainDemo {

    // ========== API 配置 ==========
    private static final String BASE_URL = "https://api.siliconflow.cn/v1";
    private static final String API_KEY = "sk-kemgxaacaqdehipgoywavavmpaiazkocfjyixcvsnslmeycv";

    // ========== Fallback 链核心 ==========
    /**
     * 单个链节点：模型名 + 构建器
     */
    static class ModelNode {
        final String name;
        final String modelId;
        final int timeoutSeconds;
        ModelNode(String name, String modelId, int timeoutSeconds) {
            this.name = name;
            this.modelId = modelId;
            this.timeoutSeconds = timeoutSeconds;
        }
        ChatModel createModel() {
            return OpenAiChatModel.builder()
                    .baseUrl(BASE_URL)
                    .apiKey(API_KEY)
                    .modelName(modelId)
                    .timeout(Duration.ofSeconds(timeoutSeconds))
                    .logRequests(false)
                    .logResponses(false)
                    .build();
        }
    }

    /**
     * Fallback 执行结果
     */
    static class FallbackResult {
        final String modelName;    // 最终用哪个模型成功的
        final String response;     // 模型响应
        final int fallbackLevel;   // 降级到第几级（0=主模型，1=备用，2=兜底）
        final String errorMessage; // 如果有降级，原因是什么
        FallbackResult(String modelName, String response, int fallbackLevel, String errorMessage) {
            this.modelName = modelName;
            this.response = response;
            this.fallbackLevel = fallbackLevel;
            this.errorMessage = errorMessage;
        }
        boolean success() {
            return response != null && !response.isEmpty();
        }
    }

    /**
     * Fallback 链：依次尝试，直到成功
     *
     * 就是责任链模式——每个节点try一下，失败了传给下一个
     */
    /**
     * Fallback 链：依次尝试，直到成功
     *
     * 就是责任链模式——每个节点try一下，失败了传给下一个
     */
    static FallbackResult executeWithFallback(List<ModelNode> chain, String prompt) {
        for (int i = 0; i < chain.size(); i++) {
            ModelNode node = chain.get(i);
            String level = switch (i) {
                case 0 -> "主模型";
                case 1 -> "备用模型";
                default -> "兜底模型(" + i + ")";
            };
            try {
                System.out.printf("  [%s] 尝试: %s (%s)...%n", level, node.name, node.modelId);
                long start = System.currentTimeMillis();
                ChatModel model = node.createModel();
                String response = model.chat(prompt);
                long latency = System.currentTimeMillis() - start;
                if (response != null && !response.isBlank()) {
                    System.out.printf("  [%s] ✅ 成功! 耗时: %dms%n", level, latency);
                    return new FallbackResult(node.name, response, i, null);
                } else {
                    System.out.printf("  [%s] ⚠️ 空响应，降级...%n", level);
                }
            } catch (Exception e) {
                System.out.printf("  [%s] ❌ 失败: %s → 降级...%n", level, e.getMessage());
            }
        }
        // 全链路都挂了
        System.out.println("  [兜底] 所有模型都不可用，返回默认响应");
        return new FallbackResult("none", "抱歉，服务暂时不可用，请稍后重试。", chain.size(), "all models failed");
    }

    // ========== 测试场景 ==========
    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("Week7 Day4 Step2: Fallback 链 — 自动降级机制");
        System.out.println("=".repeat(60));
        if (API_KEY.isEmpty()) {
            System.err.println("❌ 请先设置环境变量 SILICONFLOW_API_KEY");
            return;
        }
        // ========== 场景1：正常情况 — 主模型直接成功 ==========
        System.out.println("\n【场景1】正常情况：主模型可用，不触发降级");
        System.out.println("-".repeat(50));
        List<ModelNode> normalChain = Arrays.asList(
                new ModelNode("GLM-5.1（主力）", "Pro/zai-org/GLM-5.1", 60),
                new ModelNode("Qwen3-8B（备用）", "Qwen/Qwen3-8B", 30),
                new ModelNode("DeepSeek-V4-Flash（兜底）", "deepseek-ai/DeepSeek-V4-Flash", 15)
        );
        String prompt1 = "用一句话解释什么是RAG（检索增强生成）";
        FallbackResult result1 = executeWithFallback(normalChain, prompt1);
        printResult(result1, prompt1);
        // ========== 场景2：主模型故意配错 — 自动 fallback ==========
        System.out.println("\n【场景2】主模型不可用：自动降级到备用模型");
        System.out.println("-".repeat(50));
        List<ModelNode> brokenChain = Arrays.asList(
                new ModelNode("故意配错的模型", "this-model-does-not-exist", 5),  // 故意配错
                new ModelNode("Qwen3-8B（备用）", "Qwen/Qwen3-8B", 30),
                new ModelNode("DeepSeek-V4-Flash（兜底）", "deepseek-ai/DeepSeek-V4-Flash", 15)
        );
        String prompt2 = "用一句话解释什么是Function Calling";
        FallbackResult result2 = executeWithFallback(brokenChain, prompt2);
        printResult(result2, prompt2);
        // ========== 场景3：全部失败 — 兜底响应 ==========
        System.out.println("\n【场景3】所有模型都挂了：返回兜底响应");
        System.out.println("-".repeat(50));
        List<ModelNode> allBrokenChain = Arrays.asList(
                new ModelNode("假模型A", "fake-model-a", 5),
                new ModelNode("假模型B", "fake-model-b", 5),
                new ModelNode("假模型C", "fake-model-c", 5)
        );
        String prompt3 = "写一首关于编程的诗";
        FallbackResult result3 = executeWithFallback(allBrokenChain, prompt3);
        printResult(result3, prompt3);
        // ========== 场景4：超时降级 — 主模型超时切备用 ==========
        System.out.println("\n【场景4】主模型超时（1秒超时）：快速降级到备用模型");
        System.out.println("-".repeat(50));
        List<ModelNode> timeoutChain = Arrays.asList(
                new ModelNode("GLM-5.1（1秒超时）", "Pro/zai-org/GLM-5.1", 1),  // 故意超短
                new ModelNode("Qwen3-8B（备用）", "Qwen/Qwen3-8B", 30)
        );
        String prompt4 = "用Java写一个单例模式，双检查锁实现";
        FallbackResult result4 = executeWithFallback(timeoutChain, prompt4);
        printResult(result4, prompt4);
        // ========== 总结 ==========
        System.out.println("\n" + "=".repeat(60));
        System.out.println("Step2 完成！核心收获：");
        System.out.println("1. FallbackChain = 责任链模式，依次try直到成功");
        System.out.println("2. 主模型失败/超时/空响应 → 自动降级");
        System.out.println("3. 生产环境必备：网络抖动、API限流、模型维护时保命");
        System.out.println("4. 兜底响应让用户无感知，比直接报500好太多");
        System.out.println("5. 注意超时配置：主模型超时短一点(快速fail)，备用模型长一点(确保能接住)");
        System.out.println("=".repeat(60));
    }
    private static void printResult(FallbackResult result, String prompt) {
        System.out.println("\n  📋 结果汇总:");
        System.out.printf("  提问: %s%n", prompt.length() > 50 ? prompt.substring(0, 50) + "..." : prompt);
        System.out.printf("  最终模型: %s%n", result.modelName);
        System.out.printf("  降级级别: %d (%s)%n", result.fallbackLevel,
                result.fallbackLevel == 0 ? "主模型直接成功" : "降级了" + result.fallbackLevel + "次");
        if (result.errorMessage != null) {
            System.out.printf("  降级原因: %s%n", result.errorMessage);
        }
        System.out.printf("  响应内容: %s%n",
                result.response.length() > 200 ? result.response.substring(0, 200) + "..." : result.response);
        System.out.println();
    }
}
