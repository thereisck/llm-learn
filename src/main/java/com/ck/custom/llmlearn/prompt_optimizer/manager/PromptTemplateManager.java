package com.ck.custom.llmlearn.prompt_optimizer.manager;

import com.ck.custom.llmlearn.prompt_optimizer.model.PromptTemplateDTO;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * /**
 *  * Prompt模板管理器接口
 *  *
 *  * 核心功能：
 *  * - 模板注册（添加新模板）
 *  * - 模板查询（按ID、按分类）
 *  * - 模板渲染（生成最终Prompt）
 *  * - 模板更新/删除
 * @author changkong
 * @date 2026/4/30 14:45
 **/
public interface PromptTemplateManager {

    /**
     * 注册新模板
     *
     * @param template 模板对象
     * @return 注册成功的模板（含生成的ID）
     */
    PromptTemplateDTO register(PromptTemplateDTO template);

    /**
     * 按ID查询模板
     *
     * @param templateId 模板ID
     * @return 模板对象（Optional）
     */
    Optional<PromptTemplateDTO> getTemplate(String templateId);

    /**
     * 按分类查询所有模板
     *
     * @param category 分类名称（如：translation, code-generation）
     * @return 该分类下的所有模板
     */
    List<PromptTemplateDTO> listByCategory(String category);

    /**
     * 查询所有模板
     *
     * @return 所有模板列表
     */
    List<PromptTemplateDTO> listAll();

    /**
     * 渲染模板：用参数替换占位符，生成最终Prompt
     *
     * @param templateId 模板ID
     * @param params 参数Map（key: 变量名，value: 变量值）
     * @return 渲染后的Prompt字符串
     * @throws IllegalArgumentException 如果模板不存在或参数缺失
     */
    String render(String templateId, Map<String, String> params);

    /**
     * 更新模板
     */
    PromptTemplateDTO update(String templateId, PromptTemplateDTO template);

    /**
     * 删除模板
     */
    boolean delete(String templateId);

    /**
     * 检查模板是否存在
     */
    boolean exists(String templateId);
}
