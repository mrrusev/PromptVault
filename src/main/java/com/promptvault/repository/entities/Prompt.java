package com.promptvault.repository.entities;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("prompts")
public class Prompt {

    @Id
    private Long id;

    private String title;

    private String content;

    private Long collectionId;

    private Long ownerId;

    private Instant createdAt;

    public Prompt() {
    }

    public Prompt(Long id, String title, String content, Long collectionId, Long ownerId, Instant createdAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.collectionId = collectionId;
        this.ownerId = ownerId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Long getCollectionId() {
        return collectionId;
    }

    public void setCollectionId(Long collectionId) {
        this.collectionId = collectionId;
    }

    public Long getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(Long ownerId) {
        this.ownerId = ownerId;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
