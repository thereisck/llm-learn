package com.ck.custom.llmlearn.prompt_optimizer.client;

import lombok.Data;

/**
 *  * LLM调用配置
 *  *
 *  * 核心参数：
 *  * - model: 模型名称（gpt-3.5-turbo、gpt-4、claude-3等）
 *  * - temperature: 随机性控制（0-2，越高越随机）
 *  * - maxTokens: 最大输出Token数
 *  * - topP: 采样范围（0-1）
 * @author changkong
 * @date 2026/4/30 15:13
 **/
@Data
public class LLMConfig {

    private String model;
    private double temperature;
    private int maxTokens;
    private double topP;
    private boolean stream;

    // 默认配置
    private static final String DEFAULT_MODEL = "gpt-3.5-turbo";
    private static final double DEFAULT_TEMPERATURE = 0.7;
    private static final int DEFAULT_MAX_TOKENS = 2048;
    private static final double DEFAULT_TOP_P = 1.0;

    // ========== 构造函数 ==========

    public LLMConfig() {
        this.model = DEFAULT_MODEL;
        this.temperature = DEFAULT_TEMPERATURE;
        this.maxTokens = DEFAULT_MAX_TOKENS;
        this.topP = DEFAULT_TOP_P;
        this.stream = false;
    }

    public LLMConfig(String model) {
        this();
        this.model = model;
    }

    // ========== 静态工厂方法 ==========

    /**
     * 默认配置（GPT-3.5-turbo）
     */
    public static LLMConfig defaultConfig() {
        return new LLMConfig();
    }

    /**
     * GPT-4配置
     */
    public static LLMConfig gpt4() {
        return new LLMConfig("gpt-4");
    }

    /**
     * 高创造性配置（temperature=1.5）
     */
    public static LLMConfig creative() {
        LLMConfig config = new LLMConfig();
        config.setTemperature(1.5);
        return config;
    }

    /**
     * 高确定性配置（temperature=0.2）
     */
    public static LLMConfig deterministic() {
        LLMConfig config = new LLMConfig();
        config.setTemperature(0.2);
        return config;
    }
}
