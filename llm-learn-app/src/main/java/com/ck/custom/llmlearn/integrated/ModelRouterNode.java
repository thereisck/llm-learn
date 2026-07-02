package com.ck.custom.llmlearn.integrated;

/**
 * Week7 Day7 - Step1: 模型路由节点
 *
 * 包装 Day4 的 ModelRouterDemo 里的路由逻辑。
 * 根据用户输入的复杂度，选择小模型或大模型。
 *
 * @author changkong
 * @date 2026/7/2
 */
public class ModelRouterNode implements PipelineNode {

    public static final String SMALL_MODEL = "Qwen/Qwen3-8B";
    public static final String LARGE_MODEL = "Pro/zai-org/GLM-5.1";

    @Override
    public String getName() {
        return "ModelRouter（模型路由）";
    }

    @Override
    public void process(ChatContext ctx) {
        String model = route(ctx, ctx.userInput);
        ctx.selectedModel = model;

        System.out.println("  [ModelRouter] → " + model + " (" + ctx.taskType + ")");
    }

    /**
     * 简单路由规则：
     * - 输入很短(<20字) + 简单关键词 → 小模型
     * - 代码/推理/长文本 → 大模型
     * - 默认 → 大模型（安全优先）
     */
    private String route(ChatContext ctx, String input) {
        if (input == null || input.isBlank()) {
            return LARGE_MODEL;
        }

        // 简单任务特征：包含分类/提取/翻译/情感关键词
        String lower = input.toLowerCase();
        if (lower.contains("分类") || lower.contains("提取") ||
            lower.contains("翻译") || lower.contains("情感") ||
            lower.contains("正面/负面") || lower.contains("sentiment")) {
            ctx.taskType = "SIMPLE";
            return SMALL_MODEL;
        }

        // 复杂任务特征：代码、推理、分析
        if (input.contains("代码") || input.contains("写一个") ||
            input.contains("推理") || input.contains("分析") ||
            input.contains("设计") || input.contains("架构")) {
            ctx.taskType = "COMPLEX";
            return LARGE_MODEL;
        }

        // 默认用大模型
        ctx.taskType = "CHAT";
        return LARGE_MODEL;
    }
}
