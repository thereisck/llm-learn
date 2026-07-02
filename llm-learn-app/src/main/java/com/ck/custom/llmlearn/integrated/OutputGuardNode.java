package com.ck.custom.llmlearn.integrated;

import com.ck.custom.llmlearn.security.OutputGuard;

/**
 * Week7 Day7 - Step1: 安全输出节点
 *
 * 包装 Day5 的 OutputGuard，适配管道接口。
 * 在 LLM 调用之后执行，审查回复内容。
 *
 * @author changkong
 * @date 2026/7/2
 */
public class OutputGuardNode implements PipelineNode {

    private final OutputGuard guard;

    public OutputGuardNode() {
        this.guard = new OutputGuard();
    }

    @Override
    public String getName() {
        return "OutputGuard（输出审查）";
    }

    @Override
    public void process(ChatContext ctx) {
        OutputGuard.AuditResult result = guard.audit(ctx.llmResponse);

        System.out.println("  [OutputGuard] " + result);

        if (result.blocked) {
            ctx.outputBlocked = true;
            ctx.finalResponse = result.sanitizedResponse;
        } else {
            ctx.finalResponse = ctx.llmResponse;
        }
    }
}
