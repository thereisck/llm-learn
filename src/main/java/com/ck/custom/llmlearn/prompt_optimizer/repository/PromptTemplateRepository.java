package com.ck.custom.llmlearn.prompt_optimizer.repository;

import com.ck.custom.llmlearn.prompt_optimizer.entity.PromptTemplateEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Prompt模板 Repository（JPA）
 */
@Repository
public interface PromptTemplateRepository extends JpaRepository<PromptTemplateEntity, String> {
    
    /**
     * 查询所有未删除的模板
     */
    List<PromptTemplateEntity> findByIsDeletedFalse();
    
    /**
     * 按分类查询未删除的模板
     */
    List<PromptTemplateEntity> findByCategoryAndIsDeletedFalse(String category);
    
    /**
     * 按ID查询未删除的模板
     */
    Optional<PromptTemplateEntity> findByIdAndIsDeletedFalse(String id);
    
    /**
     * 检查模板是否存在（未删除）
     */
    boolean existsByIdAndIsDeletedFalse(String id);
}