package com.ck.custom.llmlearn.cost;

/**
 * @author changkong
 * @date 2026/6/21 16:07
 **/

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.Duration;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Week7 Day4 - Step5: 整合 Demo — Router + Fallback + Cache 三件套
 *
 * 完整请求链路：
 * 1. 请求进来 → 查缓存（命中直接返回，0 API调用）
 * 2. 未命中 → ModelRouter 选模型（简单任务→小模型，复杂任务→大模型）
 * 3. 选定模型 → FallbackChain 调用（主模型挂了→备用→兜底）
 * 4. 成功 → 存入缓存 → 返回
 * 5. 全挂了 → 兜底响应
 *
 * 这就是生产级 LLM 应用的成本优化骨架：
 * - 路由省钱：简单任务不浪费大模型
 * - 降级保命：模型挂了不影响用户
 * - 缓存省时间：相同问题不重复调
 *
 * 运行方式：直接跑main方法
 *
 * @author changkong
 * @date 2026/6/21
 */
public class IntegratedCostDemo {

    // ========== API 配置 ==========
    private static final String BASE_URL = "https://api.siliconflow.cn/v1";
    private static final String API_KEY = "sk-kemgxaacaqdehipgoywavavmpaiazkocfjyixcvsnslmeycv";
    private static final String SMALL_MODEL = "Qwen/Qwen3-8B";
    private static final String LARGE_MODEL = "Pro/zai-org/GLM-5.1";
    // ========== 任务类型 ==========
    enum TaskType {
        SIMPLE,   // 分类、提取、翻译
        COMPLEX   // 推理、代码生成、创意写作
    }
    // ========== 缓存层 ==========
    static class CacheEntry {
        final String response;
        final long timestamp;
        CacheEntry(String response) {
            this.response = response;
            this.timestamp = System.currentTimeMillis();
        }
        boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - timestamp > ttlMs;
        }
    }

    // ========== 统计 ==========
    static class Stats {
        int totalRequests = 0;
        int cacheHits = 0;
        int fallbacks = 0;
        int failures = 0;
        final Map<String, Integer> modelUsage = new HashMap<>();
        void printReport() {
            System.out.println("\n" + "=".repeat(50));
            System.out.println("📊 最终统计报告");
            System.out.println("=".repeat(50));
            System.out.printf("总请求数: %d%n", totalRequests);
            System.out.printf("缓存命中: %d (%.1f%%)%n", cacheHits, pct(cacheHits, totalRequests));
            System.out.printf("降级次数: %d (%.1f%%)%n", fallbacks, pct(fallbacks, totalRequests));
            System.out.printf("完全失败: %d%n", failures);
            System.out.println("\n模型调用分布:");
            modelUsage.forEach((model, count) ->
                    System.out.printf("  %-25s → %d 次%n", model, count));
            System.out.println("=".repeat(50));
        }
        private double pct(int part, int total) {
            return total == 0 ? 0 : part * 100.0 / total;
        }
    }

    // ========== 整合引擎 ==========
    /**
     * CostAwareChatEngine — 三件套整合
     *
     * 请求链路：Cache → Router → Fallback → Store
     */
    static class CostAwareChatEngine {
        private final Map<String, CacheEntry> cache = new HashMap<>();
        private final long cacheTtlMs;
        private final Stats stats = new Stats();
        CostAwareChatEngine(long cacheTtlMs) {
            this.cacheTtlMs = cacheTtlMs;
        }
        /**
         * 核心方法：带成本优化的 chat
         */
        String chat(String prompt, TaskType taskType) {
            stats.totalRequests++;
            String cacheKey = taskType + "::" + prompt.hashCode();
            // ① 查缓存
            CacheEntry cached = cache.get(cacheKey);
            if (cached != null && !cached.isExpired(cacheTtlMs)) {
                stats.cacheHits++;
                System.out.println("  → 💚 缓存命中，跳过API调用");
                return cached.response;
            }
            // ② 路由选模型
            String primaryModel = taskType == TaskType.SIMPLE ? SMALL_MODEL : LARGE_MODEL;
            String fallbackModel = taskType == TaskType.SIMPLE ? LARGE_MODEL : SMALL_MODEL;
            // ③ Fallback 链调用
            List<String> chain = Arrays.asList(primaryModel, fallbackModel);
            for (int i = 0; i < chain.size(); i++) {
                String modelName = chain.get(i);
                String level = i == 0 ? "主模型" : "备用模型";
                try {
                    System.out.printf("  → [%s] 调用: %s...%n", level, modelName);
                    long start = System.currentTimeMillis();
                    ChatModel model = OpenAiChatModel.builder()
                            .baseUrl(BASE_URL)
                            .apiKey(API_KEY)
                            .modelName(modelName)
                            .timeout(Duration.ofSeconds(120))
                            .build();
                    String response = model.chat(prompt);
                    long latency = System.currentTimeMillis() - start;
                    if (response != null && !response.isBlank()) {
                        System.out.printf("  → ✅ 成功 (%dms)%n", latency);
                        stats.modelUsage.merge(modelName, 1, Integer::sum);
                        if (i > 0) stats.fallbacks++;
                        // ④ 存入缓存
                        cache.put(cacheKey, new CacheEntry(response));
                        return response;
                    }
                } catch (Exception e) {
                    System.out.printf("  → ❌ 失败: %s%n", e.getMessage());
                    if (i == 0) System.out.println("  → 🟡 降级到备用模型...");
                }
            }
            // ⑤ 全挂了 → 兜底
            stats.failures++;
            String fallbackResponse = "服务暂时不可用，请稍后重试。";
            cache.put(cacheKey, new CacheEntry(fallbackResponse));
            return fallbackResponse;
        }
        Stats getStats() {
            return stats;
        }
    }

    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("Week7 Day4 Step5: 整合 Demo — 三件套实战");
        System.out.println("Router + Fallback + Cache 完整链路");
        System.out.println("=".repeat(60));
        if (API_KEY.isEmpty()) {
            System.err.println("❌ 请先设置环境变量 SILICONFLOW_API_KEY");
            return;
        }
        // 创建引擎（缓存TTL=5分钟）
        CostAwareChatEngine engine = new CostAwareChatEngine(300_000);
        // ========== 场景1：简单任务首次调用 ==========
        System.out.println("\n【场景1】简单任务（情感分类）— 首次调用");
        System.out.println("-".repeat(50));
        String q1 = "对以下评论分类，只返回正面/负面/中性：产品质量太差了，用了一周就坏了";
        System.out.println("问: " + q1);
        String a1 = engine.chat(q1, TaskType.SIMPLE);
        System.out.println("答: " + truncate(a1, 200));
        // ========== 场景2：相同问题再问 — 缓存命中 ==========
        System.out.println("\n【场景2】相同问题再问 — 缓存命中");
        System.out.println("-".repeat(50));
        System.out.println("问: " + q1);
        String a2 = engine.chat(q1, TaskType.SIMPLE);
        System.out.println("答: " + truncate(a2, 200));
        // ========== 场景3：复杂任务首次调用 ==========
        System.out.println("\n【场景3】复杂任务（代码生成）— 首次调用");
        System.out.println("-".repeat(50));
        String q3 = "用Java写一个线程安全的单例模式，双检查锁实现，给出完整代码";
        System.out.println("问: " + q3);
        String a3 = engine.chat(q3, TaskType.COMPLEX);
        System.out.println("答: " + truncate(a3, 300));
        // ========== 场景4：复杂任务重复 — 缓存命中 ==========
        System.out.println("\n【场景4】复杂任务重复提问 — 缓存命中");
        System.out.println("-".repeat(50));
        System.out.println("问: " + q3);
        String a4 = engine.chat(q3, TaskType.COMPLEX);
        System.out.println("答: " + truncate(a4, 300));
        // ========== 场景5：不同简单任务 ==========
        System.out.println("\n【场景5】另一个简单任务（信息提取）");
        System.out.println("-".repeat(50));
        String q5 = "从以下文本提取人名和职位，格式：姓名-职位。张三是阿里巴巴架构师，李四是字节技术专家";
        System.out.println("问: " + q5);
        String a5 = engine.chat(q5, TaskType.SIMPLE);
        System.out.println("答: " + truncate(a5, 200));
        // ========== 场景6：Q3第三次问 — 又命中 ==========
        System.out.println("\n【场景6】场景3的问题第三次问 — 再次命中缓存");
        System.out.println("-".repeat(50));
        System.out.println("问: " + q3);
        String a6 = engine.chat(q3, TaskType.COMPLEX);
        System.out.println("答: " + truncate(a6, 300));
        // ========== 最终统计 ==========
        engine.getStats().printReport();
        System.out.println("""
                \nStep5 完成！这就是生产级成本优化骨架：
                ┌─────────┐     ┌──────────┐     ┌────────────┐     ┌────────┐
                │ 请求进入 │ ──→ │ 查缓存   │ ──→ │ ModelRouter│ ──→ │ Fallback│
                │         │     │ 命中?返回 │     │ 选模型     │     │ 调用链  │
                └─────────┘     └──────────┘     └────────────┘     └────────┘
                                       ↑                              │
                                       │  存入缓存 ←──── 成功返回 ←────┘
                                       │
                                       ↓
                                  下次相同问题 → 0 API调用
                三件套各自职责：
                • Router  → 省钱：简单任务不浪费大模型
                • Fallback → 保命：模型挂了不影响用户
                • Cache   → 省时间+省钱：相同问题不重复调
                设计模式回顾：
                • Router   = 策略模式（Strategy）
                • Fallback = 责任链模式（Chain of Responsibility）
                • Cache    = 装饰器模式（Decorator）
                三者组合 = 生产级 LLM 应用标配""");
    }
    private static String truncate(String text, int maxLen) {
        if (text == null) return "[null]";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }
}
