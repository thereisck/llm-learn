package com.ck.custom.llmlearn.prompt;

import lombok.extern.slf4j.Slf4j;
import org.yaml.snakeyaml.Yaml;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * @author changkong
 * @date 2026/4/29 23:37
 **/
@Slf4j
public class TemplateLoader {

    private final Yaml yaml;

    public TemplateLoader() {
        this.yaml = new Yaml();
    }

    /**
     * 从文件路径加载模板
     * @param path YAML文件路径
     * @return 模板Map（key=templateId, value=PromptTemplateDO）
     */
    public Map<String, PromptTemplateDO> loadFromFile(String path) {
        try {
            Path filePath = Path.of(path);
            InputStream inputStream = Files.newInputStream(filePath);
            return loadFromStream(inputStream);
        }catch (Exception e) {
            throw new RuntimeException("加载模板文件失败: " + path, e);
        }
    }

    /**
     * 从classpath加载模板（resources目录）
     * @param resourcePath resources下的相对路径，如 "templates/template_demo.yml"
     */
    public Map<String, PromptTemplateDO> loadFromClasspath(String resourcePath) {
        InputStream inputStream = getClass().getClassLoader().getResourceAsStream(resourcePath);
        if (inputStream == null) {
            throw new RuntimeException("找不到classpath资源: " + resourcePath);
        }
        return loadFromStream(inputStream);
    }

    /**
     * 从InputStream加载模板（适用于classpath资源）
     */
    public Map<String, PromptTemplateDO> loadFromStream(InputStream inputStream) {
        Map<String, PromptTemplateDO> templateMap = new HashMap<>();
        try {
            // 解析YAML
            Map<String, Object> data = yaml.load(inputStream);
            //读取templates列表
            List<Map<String, Object>> templates = (List<Map<String, Object>>)data.get("templates");
            if (templates == null || templates.isEmpty()) {
                log.warn("YAML文件中没有找到 templates 配置");
                return templateMap;
            }
            // 遍历解析每个模板
            for (Map<String, Object> templateData : templates) {
                PromptTemplateDO template = parseTemplate(templateData);
                templateMap.put(template.getId(), template);
                log.info("加载模板: {} (v{})", template.getId(), template.getVersion());
            }
            return templateMap;
        } catch (Exception e) {
            throw new RuntimeException("加载模板失败", e);
        }
    }

    /**
     * 解析单个模板数据
     */
    private PromptTemplateDO parseTemplate(Map<String, Object> data) {
        PromptTemplateDO template = new PromptTemplateDO();

        template.setId((String) data.get("id"));
        template.setName((String) data.get("name"));
        template.setVersion((String) data.get("version"));
        template.setDescription((String) data.get("description"));

        // 注意: YAML中的 system_prompt 映射到 Java的 systemPrompt
        template.setSystemPrompt((String) data.get("system_prompt"));
        template.setUserPromptTemplate((String) data.get("user_prompt_template"));

        // 解析 variables
        List<Map<String, Object>> varsData = (List<Map<String, Object>>) data.get("variables");
        if (varsData != null) {
            List<TemplateVariableDO> variables = new ArrayList<>();
            for (Map<String, Object> varData : varsData) {
                TemplateVariableDO var = parseVariable(varData);
                variables.add(var);
            }
            template.setVariables(variables);
        }

        // 解析 metadata
        Map<String, Object> metadata = (Map<String, Object>) data.get("metadata");
        if (metadata != null) {
            template.setMetadata(metadata);
        }

        return template;
    }

    /**
     * 解析变量定义
     */
    private TemplateVariableDO parseVariable(Map<String, Object> data) {
        TemplateVariableDO var = new TemplateVariableDO();

        var.setName((String) data.get("name"));
        var.setDescription((String) data.get("description"));
        var.setType((String) data.get("type"));
        var.setRequired(Boolean.TRUE.equals(data.get("required")));
        var.setDefaultValue(data.get("defaultValue"));

        // 解析 options（用于enum类型）
        List<String> options = (List<String>) data.get("options");
        var.setOptions(options);

        return var;
    }
}
