package com.ck.custom.llmlearn.prompt_optimizer.manager;

import com.ck.custom.llmlearn.prompt_optimizer.entity.PromptTemplateEntity;
import com.ck.custom.llmlearn.prompt_optimizer.model.PromptTemplateDTO;
import com.ck.custom.llmlearn.prompt_optimizer.repository.PromptTemplateRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Prompt模板管理器实现类（数据库版本）
 * 
 * 存储：MySQL数据库（持久化）
 * 适用场景：生产环境、长期使用
 * 
 * 替代：InMemoryPromptTemplateManager（内存版本）
 */
@Slf4j
@Component
@Primary  // 优先使用数据库版本
public class DatabasePromptTemplateManager implements PromptTemplateManager {
    
    @Autowired
    private PromptTemplateRepository repository;
    
    @Override
    @Transactional
    public PromptTemplateDTO register(PromptTemplateDTO template) {
        if (template == null) {
            throw new IllegalArgumentException("模板不能为空");
        }
        
        // 生成ID（如果未提供）
        if (template.getId() == null || template.getId().isEmpty()) {
            template.setId(UUID.randomUUID().toString());
        }
        
        // 检查是否已存在
        if (repository.existsByIdAndIsDeletedFalse(template.getId())) {
            throw new IllegalArgumentException("模板ID已存在: " + template.getId());
        }
        
        // 转换为实体并保存
        PromptTemplateEntity entity = toEntity(template);
        entity.setIsDeleted(false);
        
        PromptTemplateEntity saved = repository.save(entity);
        log.info("注册模板成功: id={}, name={}", saved.getId(), saved.getName());
        
        return toDTO(saved);
    }
    
    @Override
    public Optional<PromptTemplateDTO> getTemplate(String templateId) {
        if (templateId == null || templateId.isEmpty()) {
            return Optional.empty();
        }
        
        return repository.findByIdAndIsDeletedFalse(templateId)
            .map(this::toDTO);
    }
    
    @Override
    public List<PromptTemplateDTO> listByCategory(String category) {
        if (category == null || category.isEmpty()) {
            return List.of();
        }
        
        return repository.findByCategoryAndIsDeletedFalse(category)
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    @Override
    public List<PromptTemplateDTO> listAll() {
        return repository.findByIsDeletedFalse()
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }
    
    @Override
    public String render(String templateId, Map<String, String> params) {
        PromptTemplateDTO template = getTemplate(templateId)
            .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + templateId));
        
        return template.render(params);
    }
    
    @Override
    @Transactional
    public PromptTemplateDTO update(String templateId, PromptTemplateDTO template) {
        if (!exists(templateId)) {
            throw new IllegalArgumentException("模板不存在: " + templateId);
        }
        
        PromptTemplateEntity entity = repository.findByIdAndIsDeletedFalse(templateId)
            .orElseThrow(() -> new IllegalArgumentException("模板不存在: " + templateId));
        
        // 更新字段
        entity.setName(template.getName());
        entity.setTemplate(template.getTemplate());
        entity.setCategory(template.getCategory());
        entity.setVersion(template.getVersion());
        
        PromptTemplateEntity saved = repository.save(entity);
        log.info("更新模板成功: id={}, name={}", saved.getId(), saved.getName());
        
        return toDTO(saved);
    }
    
    @Override
    @Transactional
    public boolean delete(String templateId) {
        if (templateId == null || templateId.isEmpty()) {
            throw new IllegalArgumentException("模板ID不能为空");
        }
        
        Optional<PromptTemplateEntity> entityOpt = repository.findByIdAndIsDeletedFalse(templateId);
        if (entityOpt.isEmpty()) {
            return false;
        }
        
        // 软删除（标记为已删除）
        PromptTemplateEntity entity = entityOpt.get();
        entity.setIsDeleted(true);
        repository.save(entity);
        
        log.info("删除模板成功: id={}", templateId);
        return true;
    }
    
    @Override
    public boolean exists(String templateId) {
        return templateId != null && !templateId.isEmpty() 
            && repository.existsByIdAndIsDeletedFalse(templateId);
    }
    
    // ========== 转换方法 ==========
    
    private PromptTemplateEntity toEntity(PromptTemplateDTO dto) {
        PromptTemplateEntity entity = new PromptTemplateEntity();
        entity.setId(dto.getId());
        entity.setName(dto.getName());
        entity.setTemplate(dto.getTemplate());
        entity.setCategory(dto.getCategory());
        entity.setVersion(dto.getVersion());
        entity.setCreatedAt(dto.getCreatedAt());
        entity.setUpdatedAt(dto.getUpdatedAt());
        entity.setIsDeleted(false);
        return entity;
    }
    
    private PromptTemplateDTO toDTO(PromptTemplateEntity entity) {
        PromptTemplateDTO dto = new PromptTemplateDTO();
        dto.setId(entity.getId());
        dto.setName(entity.getName());
        dto.setTemplate(entity.getTemplate());
        dto.setCategory(entity.getCategory());
        dto.setVersion(entity.getVersion());
        dto.setCreatedAt(entity.getCreatedAt());
        dto.setUpdatedAt(entity.getUpdatedAt());
        return dto;
    }
}