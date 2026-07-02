package com.ck.custom.llmlearn.integrated;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Week7 Day7 - Step1: 缓存节点
 *
 * 包装 Day4 的 CachedChatModelDemo 里的缓存逻辑。
 * 命中缓存 → 直接返回，跳过 LLM 调用。
 *
 * 简化版：用 HashMap 做 cache key = model + prompt
 * 生产环境用 Caffeine（TTL + LRU）
 *
 * @author changkong
 * @date 2026/7/2
 */
public class CacheNode implements PipelineNode {

    /** 缓存条目 */
    static class CacheEntry {
        final String response;
        final long timestamp;
        final long originalLatencyMs;

        CacheEntry(String response, long originalLatencyMs) {
            this.response = response;
            this.timestamp = System.currentTimeMillis();
            this.originalLatencyMs = originalLatencyMs;
        }

        boolean isExpired(long ttlMs) {
            return System.currentTimeMillis() - timestamp > ttlMs;
        }
    }

    private final java.util.Map<String, CacheEntry> cache = new java.util.concurrent.ConcurrentHashMap<>();
    private final long ttlMs;

    /** 缓存统计 */
    int hits = 0;
    int misses = 0;

    public CacheNode(long ttlMs) {
        this.ttlMs = ttlMs;
    }

    @Override
    public String getName() {
        return "Cache（请求缓存）";
    }

    @Override
    public void process(ChatContext ctx) {
        String cacheKey = buildCacheKey(ctx);

        CacheEntry entry = cache.get(cacheKey);
        if (entry != null && !entry.isExpired(ttlMs)) {
            // 命中缓存
            ctx.cacheHit = true;
            ctx.llmResponse = entry.response;
            ctx.finalResponse = entry.response;
            ctx.shouldStop = true; // 命中缓存，跳过 LLM 调用
            hits++;
            System.out.println("  [Cache] 💚 命中! 省了 " + entry.originalLatencyMs + "ms");
        } else {
            ctx.cacheHit = false;
            misses++;
            System.out.println("  [Cache] 🔴 未命中，继续调用LLM");
        }
    }

    /**
     * 缓存写入（LLM调用完成后由管道调用）
     */
    public void put(String userInput, String modelName, String response, long latencyMs) {
        String key = modelName + "::" + userInput.hashCode() + "::" + userInput.length();
        cache.put(key, new CacheEntry(response, latencyMs));
    }

    private String buildCacheKey(ChatContext ctx) {
        return ctx.selectedModel + "::" + ctx.userInput.hashCode() + "::" + ctx.userInput.length();
    }

    public double hitRate() {
        int total = hits + misses;
        return total == 0 ? 0 : (double) hits / total * 100;
    }

    public void printStats() {
        System.out.printf("  [Cache] 命中:%d 未命中:%d 命中率:%.1f%% 命中率%n", hits, misses, hitRate());
    }

    public void clear() {
        cache.clear();
        hits = 0;
        misses = 0;
    }
}
