package com.ck.custom.llmlearn.prompt_optimizer.manager;

import com.ck.custom.llmlearn.prompt_optimizer.model.PromptTemplateDTO;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * /**
 *  * Prompt模板管理器实现类（内存版本）
 *  *
 *  * 存储：ConcurrentHashMap（线程安全）
 *  * 适用场景：测试、小规模使用
 *  *
 *  * 后续可扩展：
 *  * - InMemoryPromptTemplateManager → DatabasePromptTemplateManager
 *  * - 使用MySQL/PostgreSQL持久化
 * @author changkong
 * @date 2026/4/30 14:48
 **/
@Component
public class InMemoryPromptTemplateManager implements PromptTemplateManager {

    // 模板存储（线程安全）
    private final Map<String, PromptTemplateDTO> templateStore = new java.util.concurrent.ConcurrentHashMap<>();

    // ID生成器
    private final IdGenerator idGenerator = new IdGenerator();

    @Override
    public PromptTemplateDTO register(PromptTemplateDTO template) {
        if(template == null) {
            throw new IllegalArgumentException("模板不能为空");
        }

        // 生成ID（如果未提供）
        if(template.getId() == null || template.getId().isEmpty()) {
            template.setId(idGenerator.generate());
        }
        if(templateStore.containsKey(template.getId())) {
            throw new IllegalArgumentException("模板ID已存在: " + template.getId());
        }
        templateStore.put(template.getId(), template);
        return template;
    }

    @Override
    public Optional<PromptTemplateDTO> getTemplate(String templateId) {
        if (templateId == null || templateId.isEmpty()) {
            return Optional.empty();
        }

        return Optional.ofNullable(templateStore.get(templateId));
    }

    @Override
    public List<PromptTemplateDTO> listByCategory(String category) {
        if(category == null || category.isEmpty()) {
            return List.of();
        }
        return templateStore.values().stream()
                .filter(template -> category.equals(template.getCategory()))
                .toList();
    }

    @Override
    public List<PromptTemplateDTO> listAll() {
        return templateStore.values().stream().toList();
    }

    @Override
    public String render(String templateId, Map<String, String> params) {
        PromptTemplateDTO templateDTO = getTemplate(templateId).orElseThrow(() -> new IllegalArgumentException("模板不存在: " + templateId));
        return templateDTO.render(params);
    }

    @Override
    public PromptTemplateDTO update(String templateId,PromptTemplateDTO template) {
        if(!exists(templateId)) {
            throw new IllegalArgumentException("模板不存在: " + templateId);
        }
        template.setId(templateId);
        templateStore.put(templateId, template);
        return template;
    }

    @Override
    public boolean delete(String templateId) {
        if(templateId == null || templateId.isEmpty()) {
            throw new IllegalArgumentException("模板ID不能为空");
        }
        return templateStore.remove(templateId) != null;
    }

    @Override
    public boolean exists(String templateId) {
        return templateId != null && !templateId.isEmpty() && templateStore.containsKey(templateId);
    }

    /**
     * 获取模版数量
     */
    public int count() {return templateStore.size();}

    /**
     * 清空所有模版
     */
    public void clear() {templateStore.clear();}

    private static class IdGenerator {
        private long counter = 0;

        public synchronized String generate() {
            counter++;
            return "template-" + counter;
        }
    }
}
