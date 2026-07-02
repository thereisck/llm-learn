package com.ck.custom.llmlearn.integrated;

import com.ck.custom.llmlearn.security.InputGuard;

/**
 * Week7 Day7 - Step1: 安全输入节点
 *
 * 包装 Day5 的 InputGuard，适配管道接口。
 * 被拦截 → 设置 shouldStop = true，后面的节点不再执行。
 *
 * @author changkong
 * @date 2026/7/2
 */
public class InputGuardNode implements PipelineNode {

    private final InputGuard guard;

    public InputGuardNode() {
        this.guard = new InputGuard();
    }

    @Override
    public String getName() {
        return "InputGuard（安全检查）";
    }

    @Override
    public void process(ChatContext ctx) {
        InputGuard.DetectionResult result = guard.check(ctx.userInput);

        System.out.println("  [InputGuard] " + result);

        if (result.blocked) {
            ctx.inputBlocked = true;
            ctx.inputBlockReason = result.ruleName + ": " + result.reason;
            ctx.finalResponse = "🚫 抱歉，您的输入被安全检查拦截。" +
                    "原因：" + result.reason;
            ctx.shouldStop = true;
        }
    }
}
