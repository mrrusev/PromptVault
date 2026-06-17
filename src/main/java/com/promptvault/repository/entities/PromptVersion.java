package com.promptvault.repository.entities;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("prompt_versions")
public class PromptVersion {

    @Id
    private Long id;

    private Long promptId;

    private Long ownerId;

    private String content;

    private Integer versionNumber;

    private Instant createdAt;

    public PromptVersion() {
    }

    public PromptVersion(Long id, Long promptId, Long ownerId, String content, Integer versionNumber,
                         Instant createdAt) {
        this.id = id;
        this.promptId = promptId;
        this.ownerId = ownerId;
        this.content = content;
        this.versionNumber = versionNumber;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getPromptId() {
        return promptId;
    }

    public void setPromptId(Long promptId) {
        this.promptId = promptId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Integer getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(Integer versionNumber) {
        this.versionNumber = versionNumber;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
