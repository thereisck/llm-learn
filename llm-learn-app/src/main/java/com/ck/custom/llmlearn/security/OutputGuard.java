package com.ck.custom.llmlearn.security;

/**
 * @author changkong
 * @date 2026/6/22 23:31
 **/

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Week7 Day5 - Step3: 输出审查器（OutputGuard）
 *
 * InputGuard 是"进门安检"，OutputGuard 是"出门安检"。
 * 就算攻击绕过了 InputGuard，如果模型回复里包含了敏感信息，
 * OutputGuard 也要拦住，不能让敏感数据出去。
 *
 * 三层检查：
 * 1. 敏感信息检测：内部代号、邮箱、密钥等是否出现在回复中
 * 2. System Prompt 泄露检测：回复中是否包含系统提示词的内容
 * 3. 越狱行为检测：回复中是否出现不该输出的违规内容
 *
 * @author changkong
 * @date 2026/6/22
 */
public class OutputGuard {

    /**
     * 敏感信息表 —— {关键词, 标签}
     * 实际项目中应该从配置文件或数据库加载，这里写死方便演示
     */
    private final String[][] sensitiveItems = {
            {"CK-2026-ALPHA", "内部代号"},
            {"admin@ck.com", "管理员邮箱"},
            {"ck-secret-", "API密钥前缀"}
    };

    /**
     * 审查结果
     */
    public static class AuditResult {
        public final boolean blocked;
        public final String ruleName;
        public final String reason;
        public final String sanitizedResponse;  // 脱敏后的回复
        AuditResult(boolean blocked, String ruleName, String reason, String sanitizedResponse) {
            this.blocked = blocked;
            this.ruleName = ruleName;
            this.reason = reason;
            this.sanitizedResponse = sanitizedResponse;
        }
        /** 通过审查 */
        static AuditResult pass(String response) {
            return new AuditResult(false, "无", "通过输出审查", response);
        }
        /** 被拦截 */
        static AuditResult block(String ruleName, String reason, String sanitized) {
            return new AuditResult(true, ruleName, reason, sanitized);
        }
        @Override
        public String toString() {
            if (!blocked) {
                return "✅ 通过输出审查";
            }
            return String.format("🚫 输出被拦截 | 规则: %s | 原因: %s", ruleName, reason);
        }
    }

    /**
     * 审查规则
     */
    public interface AuditRule {
        String getName();
        AuditResult check(String response, OutputGuard guard);
    }
    // ========== 规则1：敏感信息检测 ==========
    /**
     * 检测回复中是否包含预设的敏感信息
     * —— 内部代号、管理员邮箱、API密钥前缀等
     */
    static class SensitiveInfoRule implements AuditRule {
        @Override
        public String getName() { return "敏感信息检测"; }
        @Override
        public AuditResult check(String response, OutputGuard guard) {
            String sanitized = response;
            boolean found = false;
            String matchedItem = "";
            // 逐个检查敏感词
            for (String[] item : guard.sensitiveItems) {
                String keyword = item[0];
                String label = item[1];
                if (response.contains(keyword)) {
                    found = true;
                    matchedItem = label;
                    // 脱敏：用 *** 替换敏感信息
                    sanitized = sanitized.replace(keyword, "***");
                }
            }
            if (found) {
                return AuditResult.block(getName(),
                        "回复中包含敏感信息：" + matchedItem + "（已脱敏）",
                        sanitized);
            }
            return AuditResult.pass(response);
        }
    }

    // ========== 规则2：System Prompt 泄露检测 ==========
    /**
     * 检测回复中是否泄露了 System Prompt 的内容
     * —— 如果回复里出现了"你是CK公司客服""你的职责"等系统提示词片段
     */
    static class PromptLeakRule implements AuditRule {
        private final List<Pattern> patterns = Arrays.asList(
                // 模型直接复述系统提示词
                Pattern.compile("我的(系统提示词|指令|规则)是", Pattern.CASE_INSENSITIVE),
                Pattern.compile("(System Prompt|系统提示词).*内容", Pattern.CASE_INSENSITIVE),
                Pattern.compile("我被设定为", Pattern.CASE_INSENSITIVE),
                Pattern.compile("我的职责是", Pattern.CASE_INSENSITIVE),
                // 模型声称要"调试"或"展示"内部配置
                Pattern.compile("(调试|展示|显示|输出).*(配置|设定|指令)", Pattern.CASE_INSENSITIVE)
        );
        @Override
        public String getName() { return "System Prompt泄露检测"; }
        @Override
        public AuditResult check(String response, OutputGuard guard) {
            for (Pattern p : patterns) {
                if (p.matcher(response).find()) {
                    return AuditResult.block(getName(),
                            "回复中可能泄露了System Prompt内容",
                            "[回复已被屏蔽：检测到System Prompt泄露]");
                }
            }
            return AuditResult.pass(response);
        }
    }

    // ========== 规则3：越狱行为检测 ==========
    /**
     * 检测回复中是否出现不该输出的违规内容
     * —— 比如模型真的扮演了"黑客助手"，输出了攻击代码等
     */
    static class JailbreakContentRule implements AuditRule {
        private final List<Pattern> patterns = Arrays.asList(
                // 模型声称自己是"无限制"的角色
                Pattern.compile("我是.*(DAN|黑客助手|没有限制|不受限制)", Pattern.CASE_INSENSITIVE),
                // 模型输出明确的攻击/入侵指令
                Pattern.compile("(SQL注入|XSS攻击|DDoS|木马|后门).*(教程|步骤|方法)", Pattern.CASE_INSENSITIVE),
                // 模型输出了完整的密钥/凭证
                Pattern.compile("(password|passwd|secret|api.?key).*(=|:).*[a-zA-Z0-9]{16,}", Pattern.CASE_INSENSITIVE)
        );
        @Override
        public String getName() { return "越狱行为检测"; }
        @Override
        public AuditResult check(String response, OutputGuard guard) {
            for (Pattern p : patterns) {
                if (p.matcher(response).find()) {
                    return AuditResult.block(getName(),
                            "回复中检测到越狱内容：模型可能已被劫持",
                            "[回复已被屏蔽：检测到越狱行为]");
                }
            }
            return AuditResult.pass(response);
        }
    }

    // ========== OutputGuard 主体 ==========
    private final List<AuditRule> rules;
    public OutputGuard() {
        this.rules = Arrays.asList(
                new SensitiveInfoRule(),
                new PromptLeakRule(),
                new JailbreakContentRule()
        );
    }

    /**
     * 核心方法：审查 LLM 的回复
     * 责任链模式 —— 依次过每条规则
     *
     * @param response LLM 的原始回复
     * @return 审查结果（包含脱敏后的回复）
     */
    public AuditResult audit(String response) {
        if (response == null || response.isBlank()) {
            return AuditResult.pass(response);
        }
        String current = response;
        for (AuditRule rule : rules) {
            AuditResult result = rule.check(current, this);
            if (result.blocked) {
                // 如果是敏感信息，用脱敏后的回复继续检查其他规则
                if (rule instanceof SensitiveInfoRule) {
                    current = result.sanitizedResponse;
                    continue;
                }
                // 其他规则直接拦截
                return result;
            }
        }
        // 检查是否经过了脱敏
        if (!current.equals(response)) {
            return AuditResult.block("敏感信息检测",
                    "回复中包含敏感信息（已脱敏）",
                    current);
        }
        return AuditResult.pass(response);
    }

    /**
     * 打印已加载的规则和敏感词表
     */
    public void printConfig() {
        System.out.println("OutputGuard 已加载规则：");
        for (int i = 0; i < rules.size(); i++) {
            System.out.printf("  %d. %s%n", i + 1, rules.get(i).getName());
        }
        System.out.println("敏感信息监控表：");
        for (String[] item : sensitiveItems) {
            System.out.printf("  - %-20s → %s%n", item[0], item[1]);
        }
    }
}
