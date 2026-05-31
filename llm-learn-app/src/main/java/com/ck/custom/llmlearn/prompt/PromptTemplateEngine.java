package com.ck.custom.llmlearn.prompt;

import dev.langchain4j.model.input.Prompt;
import lombok.extern.slf4j.Slf4j;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * /**
 *  * Prompt模板渲染引擎
 *  *
 *  * 功能：
 *  * 1. 从YAML加载模板定义
 *  * 2. 变量校验（必填、类型、enum范围）
 *  * 3. 变量注入（使用LangChain4j的PromptTemplate）
 *  * 4. 返回渲染后的Prompt对象
 *  *
 *  * 使用方式：
 *  * <pre>
 *  * PromptTemplateEngine engine = new PromptTemplateEngine();
 *  * engine.loadTemplates("templates/template_demo.yml");
 *  *
 *  * Prompt prompt = engine.render("code_review", Map.of(
 *  *     "language", "java",
 *  *     "code", "public class Foo { ... }"
 *  * ));
 *  * </pre>
 *
 * @author changkong
 * @date 2026/4/29 23:02
 **/
@Slf4j
public class PromptTemplateEngine {

    /**
     * 模板存储（key=templateId, value=PromptTemplate）
     */
    private Map<String, PromptTemplateDO> templates;

    /**
     * 模板加载器
     */
    private final TemplateLoader loader;

    public PromptTemplateEngine() {
        this.loader = new TemplateLoader();
        this.templates = new HashMap<>();
    }

    /**
     * 从classpath加载模板
     * @param resourcePath resources下的相对路径
     */
    public void loadFromClasspath(String resourcePath) {
        templates = loader.loadFromClasspath(resourcePath);
        log.info("已加载 {} 个模板", templates.size());
    }

    /**
     * 从文件路径加载模板
     * @param path 文件绝对路径
     */
    public void loadFromFile(String path) {
        templates = loader.loadFromFile(path);
        log.info("已加载 {} 个模板", templates.size());
    }

    /**
     * 渲染模板
     * @param templateId 模板ID
     * @param variables 变量值（key=变量名, value=变量值）
     * @return 渲染后的Prompt对象
     */
    public RenderedPromptDO render(String templateId, Map<String, Object> variables) {
        if (!templates.containsKey(templateId)) {
            throw new IllegalArgumentException("未找到模板: " + templateId);
        }
        PromptTemplateDO template = templates.get(templateId);

        //合并默认值
        Map<String, Object> finalVariables = mergeDefaultValues(template, variables);

        //校验变量
        validateVariables(template, finalVariables);

        //渲染System Prompt和User Prompt
        String systemPrompt = renderTemplate(template.getSystemPrompt(), finalVariables);

        //渲染User Prompt
        String userPrompt = renderTemplate(template.getUserPromptTemplate(), finalVariables);

        log.debug("模板渲染成功: {} (变量数: {})", templateId, finalVariables.size());
        return new RenderedPromptDO(templateId, systemPrompt, userPrompt, template.getMetadata());
    }

    /**
     * 渲染单个Prompt文本（使用LangChain4j）
     */
    private String renderTemplate(String templateText, Map<String, Object> variables) {
        if (templateText == null || templateText.trim().isEmpty()) {
            return "";
        }

        // 使用LangChain4j的PromptTemplate渲染
        dev.langchain4j.model.input.PromptTemplate langchainTemplate =
                dev.langchain4j.model.input.PromptTemplate.from(templateText);

        Prompt prompt = langchainTemplate.apply(variables);
        return prompt.text();
    }

    /**
     * 校验变量
     */
    private void validateVariables(PromptTemplateDO template, Map<String, Object> variables) {
        List<TemplateVariableDO> varDefs = template.getVariables();

        if (varDefs == null) {
            return;
        }

        for (TemplateVariableDO varDef : varDefs) {
            Object value = variables.get(varDef.getName());
            varDef.validate(value);
        }

        // 检查必填项是否全部提供
        List<String> requiredVars = template.getRequiredVariables();
        for (String reqVar : requiredVars) {
            if (!variables.containsKey(reqVar) || variables.get(reqVar) == null) {
                throw new ValidationException("必填变量 '" + reqVar + "' 未提供值");
            }
        }
    }

    /**
     * 合并默认值（用户传入值优先，缺失时使用默认值）
     */
    private Map<String, Object> mergeDefaultValues(PromptTemplateDO template, Map<String, Object> userVariables) {
        Map<String, Object> merged = new HashMap<>();

        // 先加入默认值
        Map<String, Object> defaults = template.getDefaultValues();
        merged.putAll(defaults);

        // 再加入用户传入值（覆盖默认值）
        merged.putAll(userVariables);

        return merged;
    }

    /**
     * 获取模板列表（用于展示可用模板）
     */
    public Map<String, PromptTemplateDO> getTemplates() {
        return templates;
    }

    /**
     * 获取单个模板
     */
    public PromptTemplateDO getTemplate(String templateId) {
        return templates.get(templateId);
    }

    /**
     * 检查模板是否存在
     */
    public boolean hasTemplate(String templateId) {
        return templates.containsKey(templateId);
    }
}
