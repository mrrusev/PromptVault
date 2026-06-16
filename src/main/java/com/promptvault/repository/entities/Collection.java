package com.promptvault.repository.entities;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("collections")
public class Collection {

    @Id
    private Long id;

    private String name;

    private Long ownerId;

    private Instant createdAt;

    public Collection() {
    }

    public Collection(Long id, String name, Long ownerId, Instant createdAt) {
        this.id = id;
        this.name = name;
        this.ownerId = ownerId;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
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
