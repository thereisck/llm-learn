package com.ck.custom.llmlearn.cost;

/**
 * @author changkong
 * @date 2026/6/21 13:06
 **/

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Week7 Day4 - Step3: 请求缓存 — 相同问题不重复调API
 *
 * 核心思路：
 * - prompt + 模型名 → 拼成 cache key
 * - 命中缓存 → 直接返回，不调API（省钱+省时间）
 * - 未命中 → 调API → 存入缓存 → 返回
 *
 * 设计模式：装饰器模式（Decorator）
 * 包装现有的 ChatModel，加一层缓存逻辑
 * Spring 里各种 TransactionAwareCacheDecorator 之类，本质一样
 *
 * 生产环境用 Caffeine（支持TTL过期+LRU淘汰+异步刷新）
 * 这里先用 HashMap 演示核心逻辑，简单直接
 *
 * 运行方式：直接跑main方法
 * 观察重点：
 * 1. 第一次提问 → 调API（慢）
 * 2. 完全相同的问题 → 命中缓存（瞬间返回）
 * 3. 问题稍有不同 → 未命中（重新调API）
 * 4. 缓存命中率统计
 *
 * @author changkong
 * @date 2026/6/21
 */
public class CachedChatModelDemo {

    // ========== API 配置 ==========
    private static final String BASE_URL = "https://api.siliconflow.cn/v1";
    private static final String API_KEY = "sk-kemgxaacaqdehipgoywavavmpaiazkocfjyixcvsnslmeycv";
    private static final String MODEL_NAME = "Pro/zai-org/GLM-5.1";

    // ========== 缓存装饰器核心 ==========
    /**
     * 缓存条目
     */
    static class CacheEntry {
        final String response;
        final long timestamp;
        final long latencyMs; // 原始调用耗时，用于对比展示
        CacheEntry(String response, long latencyMs) {
            this.response = response;
            this.timestamp = System.currentTimeMillis();
            this.latencyMs = latencyMs;
        }
        boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - timestamp > ttlMs;
        }
    }

    /**
     * 缓存统计
     */
    static class CacheStats {
        int hits = 0;
        int misses = 0;
        long savedLatencyMs = 0; // 缓存命中省了多少时间
        double hitRate() {
            int total = hits + misses;
            return total == 0 ? 0 : (double) hits / total * 100;
        }
        void printReport() {
            int total = hits + misses;
            System.out.println("\n========== 缓存统计报告 ==========");
            System.out.printf("总请求: %d | 命中: %d | 未命中: %d%n", total, hits, misses);
            System.out.printf("命中率: %.1f%%%n", hitRate());
            System.out.printf("累计节省: %dms (约 %.1f 秒)%n", savedLatencyMs, savedLatencyMs / 1000.0);
            System.out.println("==================================\n");
        }
    }

    /**
     * CachedChatModel — 装饰器模式核心
     *
     * 包装任意 ChatModel，加一层缓存
     * 被包装的对象不知道自己被缓存了（透明装饰）
     */
    static class CachedChatModel {
        private final ChatModel delegate;        // 被装饰的真实模型
        private final Map<String, CacheEntry> cache = new HashMap<>();
        private final long ttlMs;                 // 缓存过期时间
        private final CacheStats stats = new CacheStats();
        CachedChatModel(ChatModel delegate, long ttlMs) {
            this.delegate = delegate;
            this.ttlMs = ttlMs;
        }
        /**
         * 核心：带缓存的 chat 调用
         */
        String chat(String prompt) {
            String cacheKey = buildCacheKey(prompt);
            // 1. 查缓存
            CacheEntry entry = cache.get(cacheKey);
            if (entry != null && !entry.isExpired(ttlMs)) {
                stats.hits++;
                stats.savedLatencyMs += entry.latencyMs;
                System.out.printf("  💚 缓存命中! 省了 %dms%n", entry.latencyMs);
                return entry.response;
            }
            // 2. 未命中 → 调真实API
            if (entry != null) {
                System.out.println("  🟡 缓存已过期，重新调用...");
                stats.hits--; // 不算命中
            } else {
                System.out.println("  🔴 缓存未命中，调用API...");
            }
            stats.misses++;
            long start = System.currentTimeMillis();
            String response = delegate.chat(prompt);
            long latency = System.currentTimeMillis() - start;
            // 3. 存入缓存
            cache.put(cacheKey, new CacheEntry(response, latency));
            System.out.printf("  ✅ API调用完成，耗时 %dms，已存入缓存%n", latency);
            return response;
        }
        /**
         * 构建 cache key：模型名 + prompt
         * 不同模型即使相同prompt，答案不同，不能共享缓存
         */
        private String buildCacheKey(String prompt) {
            return MODEL_NAME + "::" + prompt.hashCode() + "::" + prompt.length();
            // 用 hashCode + length 做唯一标识
            // 生产环境用 SHA-256 更严谨，这里演示用 hashCode 够了
        }
        CacheStats getStats() {
            return stats;
        }
        /**
         * 手动清除缓存
         */
        void clearCache() {
            cache.clear();
            System.out.println("  🗑️ 缓存已清空");
        }
        /**
         * 查看当前缓存条目数
         */
        int cacheSize() {
            return cache.size();
        }
    }

    // ========== 测试场景 ==========
    public static void main(String[] args) {
        System.out.println("=".repeat(60));
        System.out.println("Week7 Day4 Step3: 请求缓存 CachedChatModel");
        System.out.println("=".repeat(60));
        if (API_KEY.isEmpty()) {
            System.err.println("❌ 请先设置环境变量 SILICONFLOW_API_KEY");
            return;
        }
        // 创建底层模型
        ChatModel realModel = OpenAiChatModel.builder()
                .baseUrl(BASE_URL)
                .apiKey(API_KEY)
                .modelName(MODEL_NAME)
                .timeout(Duration.ofSeconds(120))
                .build();
        // 用缓存装饰器包装（TTL = 5分钟 = 300000ms）
        CachedChatModel cachedModel = new CachedChatModel(realModel, 300_000);
        // ========== 场景1：首次调用 → 未命中 → 调API ==========
        System.out.println("\n【场景1】首次提问（未命中，调API）");
        System.out.println("-".repeat(50));
        String q1 = "什么是RAG？用一句话解释";
        System.out.println("问: " + q1);
        String a1 = cachedModel.chat(q1);
        System.out.println("答: " + truncate(a1, 150));
        System.out.printf("当前缓存条目: %d%n", cachedModel.cacheSize());
        // ========== 场景2：完全相同的问题 → 命中缓存 ==========
        System.out.println("\n【场景2】完全相同的问题再问一次（命中缓存）");
        System.out.println("-".repeat(50));
        System.out.println("问: " + q1);
        String a2 = cachedModel.chat(q1);
        System.out.println("答: " + truncate(a2, 150));
        System.out.printf("当前缓存条目: %d%n", cachedModel.cacheSize());
        // ========== 场景3：不同的问题 → 未命中 ==========
        System.out.println("\n【场景3】换一个问题（未命中）");
        System.out.println("-".repeat(50));
        String q3 = "什么是Function Calling？用一句话解释";
        System.out.println("问: " + q3);
        String a3 = cachedModel.chat(q3);
        System.out.println("答: " + truncate(a3, 150));
        System.out.printf("当前缓存条目: %d%n", cachedModel.cacheSize());
        // ========== 场景4：第二个问题再问一次 → 命中 ==========
        System.out.println("\n【场景4】第二个问题重复提问（命中缓存）");
        System.out.println("-".repeat(50));
        System.out.println("问: " + q3);
        String a4 = cachedModel.chat(q3);
        System.out.println("答: " + truncate(a4, 150));
        // ========== 场景5：问题稍有不同 → 未命中 ==========
        System.out.println("\n【场景5】问题措辞略改（未命中，说明缓存是精确匹配）");
        System.out.println("-".repeat(50));
        String q5 = "什么是RAG？请用一句话解释";  // 多了个"请"字
        System.out.println("问: " + q5);
        String a5 = cachedModel.chat(q5);
        System.out.println("答: " + truncate(a5, 150));
        System.out.printf("当前缓存条目: %d%n", cachedModel.cacheSize());
        // ========== 缓存统计报告 ==========
        cachedModel.getStats().printReport();
        // ========== 清除缓存后再问 → 未命中 ==========
        System.out.println("【场景6】清空缓存后再问（未命中）");
        System.out.println("-".repeat(50));
        cachedModel.clearCache();
        System.out.println("问: " + q1);
        String a6 = cachedModel.chat(q1);
        System.out.println("答: " + truncate(a6, 150));
        // ========== 最终统计 ==========
        cachedModel.getStats().printReport();
        // ========== 总结 ==========
        System.out.println("=".repeat(60));
        System.out.println("Step3 完成！核心收获：");
        System.out.println("1. CachedChatModel = 装饰器模式，透明地给 ChatModel 加缓存");
        System.out.println("2. cache key = 模型名 + prompt哈希 + prompt长度（精确匹配）");
        System.out.println("3. 缓存命中 → 省钱+省时间（0 API调用）");
        System.out.println("4. 生产环境用 Caffeine：支持 LRU淘汰 + TTL过期 + 异步刷新");
        System.out.println("5. 注意：缓存是精确匹配，差一个字都不行");
        System.out.println("   → 进阶：语义缓存（用Embedding相似度匹配），Step4会提到");
        System.out.println("=".repeat(60));
    }
    private static String truncate(String text, int maxLen) {
        if (text == null) return "[null]";
        return text.length() > maxLen ? text.substring(0, maxLen) + "..." : text;
    }

}
