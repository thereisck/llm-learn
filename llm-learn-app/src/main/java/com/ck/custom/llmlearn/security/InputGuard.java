package com.ck.custom.llmlearn.security;

/**
 * @author changkong
 * @date 2026/6/22 23:00
 **/

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Week7 Day5 - Step2: 输入安全过滤器（InputGuard）
 *
 * 在用户请求到达LLM之前，检测并拦截恶意Prompt。
 * 基于规则匹配，覆盖Step1演示的四种攻击类型：
 * 1. 指令覆盖检测：匹配"忽略指令""忘记之前"等关键词
 * 2. 角色劫持检测：匹配"[系统消息]""管理员模式"等伪装
 * 3. 数据泄露检测：匹配"翻译指令""输出提示词"等套话
 * 4. 间接注入检测：匹配"文档里说""IMPORTANT:"等嵌套指令
 *
 * 设计模式：责任链 —— 每条规则依次检查，命中任一条就拦截
 *
 * @author changkong
 * @date 2026/6/22
 */
public class InputGuard {

    /**
     * 检测结果
     */
    public static class DetectionResult {
        public final boolean blocked;        // 是否拦截
        public final String ruleName;        // 命中的规则名
        public final String reason;           // 拦截原因
        public final RiskLevel riskLevel;     // 风险等级
        DetectionResult(boolean blocked, String ruleName, String reason, RiskLevel riskLevel) {
            this.blocked = blocked;
            this.ruleName = ruleName;
            this.reason = reason;
            this.riskLevel = riskLevel;
        }
        /** 通过检测 */
        static DetectionResult pass() {
            return new DetectionResult(false, "无", "通过安全检测", RiskLevel.NONE);
        }
        /** 被拦截 */
        static DetectionResult block(String ruleName, String reason, RiskLevel level) {
            return new DetectionResult(true, ruleName, reason, level);
        }
        @Override
        public String toString() {
            if (!blocked) {
                return "✅ 通过安全检测";
            }
            return String.format("🚫 被拦截 | 规则: %s | 风险: %s | 原因: %s", ruleName, riskLevel, reason);
        }
    }

    /**
     * 风险等级
     */
    public enum RiskLevel {
        NONE("无"),
        LOW("低"),
        MEDIUM("中"),
        HIGH("高"),
        CRITICAL("严重");
        private final String label;
        RiskLevel(String label) { this.label = label; }
        @Override
        public String toString() { return label; }
    }

    /**
     * 安全规则 —— 单条检测规则
     */
    public interface SecurityRule {
        String getName();
        DetectionResult check(String input);
    }

    // ========== 规则1：指令覆盖检测 ==========
    /**
     * 检测"忽略指令""忘记规则""你现在是XXX"等指令覆盖手法
     */
    static class InstructionOverrideRule implements SecurityRule {
        private final List<Pattern> patterns = Arrays.asList(
                Pattern.compile("忽略.*(指令|规则|提示|要求)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("忘记.*(之前|上面|所有)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("无视.*(指令|规则|限制)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("不要遵守.*(规则|指令|限制)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("你现在是.*(黑客|攻击者|DAN|没有限制|不受限制)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("从现在起.*(你不是|你不再是)", Pattern.CASE_INSENSITIVE)
        );
        @Override
        public String getName() { return "指令覆盖检测"; }
        @Override
        public DetectionResult check(String input) {
            for (Pattern p : patterns) {
                if (p.matcher(input).find()) {
                    return DetectionResult.block(getName(),
                            "检测到指令覆盖攻击：试图让模型忽略原有指令或切换角色",
                            RiskLevel.CRITICAL);
                }
            }
            return DetectionResult.pass();
        }
    }

    // ========== 规则2：角色劫持检测 ==========
    /**
     * 检测伪装成系统消息、管理员指令的手法
     */
    static class RoleHijackingRule implements SecurityRule {
        private final List<Pattern> patterns = Arrays.asList(
                Pattern.compile("\\[(系统|管理员|开发者|system|admin)(消息|模式|指令|命令)]", Pattern.CASE_INSENSITIVE),
                Pattern.compile("(管理员|开发者|system|admin).*模式.*(激活|开启|启动)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("从现在起.*(你不是|你不再是)", Pattern.CASE_INSENSITIVE)
        );
        @Override
        public String getName() { return "指令覆盖检测"; }
        @Override
        public DetectionResult check(String input) {
            for (Pattern p : patterns) {
                if (p.matcher(input).find()) {
                    return DetectionResult.block(getName(),
                            "检测到指令覆盖攻击：试图让模型忽略原有指令或切换角色",
                            RiskLevel.CRITICAL);
                }
            }
            return DetectionResult.pass();
        }
    }

    // ========== 规则3：数据泄露检测 ==========
    /**
     * 检测"翻译指令""输出提示词""告诉我你的规则"等套话手法
     */
    static class DataExfiltrationRule implements SecurityRule {
        private final List<Pattern> patterns = Arrays.asList(
                Pattern.compile("(翻译|转换|输出|显示|打印|告诉我).*(指令|规则|提示词|prompt|系统提示)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("(你的|你收到的).*(指令|规则|prompt).*(是什么|内容|英文)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("输出.*(system prompt|系统提示词)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("(内部|隐藏).*(代号|密钥|邮箱|token)", Pattern.CASE_INSENSITIVE)
        );
        @Override
        public String getName() { return "数据泄露检测"; }
        @Override
        public DetectionResult check(String input) {
            for (Pattern p : patterns) {
                if (p.matcher(input).find()) {
                    return DetectionResult.block(getName(),
                            "检测到数据泄露攻击：试图诱导模型输出内部指令或敏感信息",
                            RiskLevel.HIGH);
                }
            }
            return DetectionResult.pass();
        }
    }

    // ========== 规则4：间接注入检测 ==========
    /**
     * 检测把攻击指令藏在"引用数据"里的手法
     */
    static class IndirectInjectionRule implements SecurityRule {
        private final List<Pattern> patterns = Arrays.asList(
                Pattern.compile("(文档|文章|网页|邮件).*(看到|写着|提到|说).*请", Pattern.CASE_INSENSITIVE),
                Pattern.compile("IMPORTANT:", Pattern.CASE_INSENSITIVE),
                Pattern.compile("(请告诉用户|请回复|请输出).*(密钥|密码|token|secret)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("'[^']{20,}.*请[^']*'", Pattern.CASE_INSENSITIVE)
        );
        @Override
        public String getName() { return "间接注入检测"; }
        @Override
        public DetectionResult check(String input) {
            for (Pattern p : patterns) {
                if (p.matcher(input).find()) {
                    return DetectionResult.block(getName(),
                            "检测到间接注入攻击：恶意指令可能藏在引用数据中",
                            RiskLevel.HIGH);
                }
            }
            return DetectionResult.pass();
        }
    }

    // ========== 规则5：编码绕过检测（附加） ==========
    /**
     * 检测Base64、Unicode等编码伪装
     */
    static class EncodingAttackRule implements SecurityRule {
        private final List<Pattern> patterns = Arrays.asList(
                Pattern.compile("base64.*解码", Pattern.CASE_INSENSITIVE),
                Pattern.compile("解码.*(base64|unicode|hex|十六进制)", Pattern.CASE_INSENSITIVE),
                Pattern.compile("[A-Za-z0-9+/]{40,}={0,2}")  // 疑似Base64长串
        );
        @Override
        public String getName() { return "编码绕过检测"; }
        @Override
        public DetectionResult check(String input) {
            for (Pattern p : patterns) {
                if (p.matcher(input).find()) {
                    return DetectionResult.block(getName(),
                            "检测到编码绕过攻击：输入中包含可疑编码内容",
                            RiskLevel.MEDIUM);
                }
            }
            return DetectionResult.pass();
        }
    }

    // ========== InputGuard 主体 ==========
    private final List<SecurityRule> rules;
    public InputGuard() {
        this.rules = Arrays.asList(
                new InstructionOverrideRule(),
                new RoleHijackingRule(),
                new DataExfiltrationRule(),
                new IndirectInjectionRule(),
                new EncodingAttackRule()
        );
    }
    /**
     * 核心方法：检测用户输入
     * 责任链模式 —— 依次过每条规则，命中任一条就拦截
     *
     * @param userInput 用户输入
     * @return 检测结果
     */
    public DetectionResult check(String userInput) {
        if (userInput == null || userInput.isBlank()) {
            return DetectionResult.pass();
        }
        List<DetectionResult> warnings = new ArrayList<>();
        for (SecurityRule rule : rules) {
            DetectionResult result = rule.check(userInput);
            if (result.blocked) {
                // CRITICAL 和 HIGH 直接拦截
                if (result.riskLevel == RiskLevel.CRITICAL || result.riskLevel == RiskLevel.HIGH) {
                    return result;
                }
                // MEDIUM 和 LOW 记录警告，继续检测
                warnings.add(result);
            }
        }
        // 有警告但没到拦截级别
        if (!warnings.isEmpty()) {
            DetectionResult first = warnings.get(0);
            return DetectionResult.block(first.ruleName,
                    "可疑输入（低风险）：" + first.reason,
                    first.riskLevel);
        }
        return DetectionResult.pass();
    }
    /**
     * 获取所有规则名（用于打印配置信息）
     */
    public void printRules() {
        System.out.println("InputGuard 已加载规则：");
        for (int i = 0; i < rules.size(); i++) {
            System.out.printf("  %d. %s%n", i + 1, rules.get(i).getName());
        }
    }
}
