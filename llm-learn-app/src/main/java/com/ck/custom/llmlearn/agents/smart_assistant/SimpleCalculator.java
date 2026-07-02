package com.ck.custom.llmlearn.agents.smart_assistant;

/**
 * 纯Java数学表达式计算器（递归下降解析器）
 *
 * 为什么需要这个？
 * Java 15+移除了Nashorn JS引擎，ScriptEngineManager.getEngineByName("js")返回null
 * 所以 SmartAssistantTools.calculate() 不能再用 ScriptEngine.eval()
 *
 * 递归下降解析器原理：
 * 把数学表达式看成一颗语法树，从上到下逐层解析：
 *
 * 表达式 = 加减项 (+|-) 加减项 (+|-) ...
 * 加减项 = 乘除模项 (*|/|%) 乘除模项 ...
 * 乘除模项 = 因子
 * 因子 = 数字 | (表达式) | -因子（负数）
 *
 * 运算优先级：括号 > 负号 > 乘除模 > 加减（跟数学规则一致）
 *
 * 支持：加减乘除取模 + 括号 + 负数 + 小数
 * 不支持：幂运算（**）→ 太容易误解析，用Math.pow替代
 */
public class SimpleCalculator {

    private final String input;
    private int pos;  // 当前解析位置

    public SimpleCalculator(String input) {
        this.input = input.trim();
        this.pos = 0;
    }

    public static double eval(String expression) {
        SimpleCalculator calc = new SimpleCalculator(expression);
        double result = calc.parseExpression();
        if (calc.pos < calc.input.length()) {
            throw new IllegalArgumentException("意外的字符: '" + calc.current() + "' 在位置 " + calc.pos);
        }
        return result;
    }

    // ========== 解析层1：加减（最低优先级） ==========

    private double parseExpression() {
        double result = parseTerm();
        while (hasMore() && (current() == '+' || current() == '-')) {
            char op = consume();
            double next = parseTerm();
            result = op == '+' ? result + next : result - next;
        }
        return result;
    }

    // ========== 解析层2：乘除模 ==========

    private double parseTerm() {
        double result = parseFactor();
        while (hasMore() && (current() == '*' || current() == '/' || current() == '%')) {
            char op = consume();
            double next = parseFactor();
            switch (op) {
                case '*': result *= next; break;
                case '/':
                    if (next == 0) throw new ArithmeticException("除数为0");
                    result /= next; break;
                case '%': result %= next; break;
            }
        }
        return result;
    }

    // ========== 解析层3：因子（数字/括号/负数） ==========

    private double parseFactor() {
        // 负数处理：-xxx
        if (hasMore() && current() == '-') {
            consume();
            return -parseFactor();
        }

        // 正号处理：+xxx（忽略）
        if (hasMore() && current() == '+') {
            consume();
            return parseFactor();
        }

        // 括号处理：(expression)
        if (hasMore() && current() == '(') {
            consume();  // 吃掉 '('
            double result = parseExpression();
            if (!hasMore() || current() != ')') {
                throw new IllegalArgumentException("缺少右括号");
            }
            consume();  // 吃掉 ')'
            return result;
        }

        // 数字处理：整数或小数
        return parseNumber();
    }

    // ========== 解析数字 ==========

    private double parseNumber() {
        skipWhitespace();
        int start = pos;
        while (hasMore() && (Character.isDigit(current()) || current() == '.')) {
            pos++;
        }
        if (start == pos) {
            throw new IllegalArgumentException("期望数字，但遇到: '" + (hasMore() ? current() : "结束") + "'");
        }
        String numStr = input.substring(start, pos);
        skipWhitespace();
        return Double.parseDouble(numStr);
    }

    // ========== 辅助方法 ==========

    private boolean hasMore() {
        return pos < input.length();
    }

    private char current() {
        return input.charAt(pos);
    }

    private char consume() {
        skipWhitespace();
        char c = input.charAt(pos);
        pos++;
        skipWhitespace();
        return c;
    }

    private void skipWhitespace() {
        while (hasMore() && Character.isWhitespace(current())) {
            pos++;
        }
    }
}
