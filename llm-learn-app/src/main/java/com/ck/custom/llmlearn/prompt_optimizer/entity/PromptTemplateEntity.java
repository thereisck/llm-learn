package com.ck.custom.llmlearn.prompt_optimizer.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.Map;

/**
 * Prompt模板实体类（数据库持久化）
 * 
 * 对应表：prompt_template
 */
@Data
@Entity
@Table(name = "prompt_template")
public class PromptTemplateEntity {
    
    @Id
    @Column(length = 100)
    private String id;
    
    @Column(nullable = false, length = 200)
    private String name;
    
    @Column(columnDefinition = "TEXT")
    private String template;
    
    @Column(length = 50)
    private String category;
    
    @Column(length = 20)
    private String version;
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    @Column(name = "is_deleted")
    private Boolean isDeleted = false;
    
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (isDeleted == null) {
            isDeleted = false;
        }
    }
    
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}