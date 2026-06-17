package com.promptvault.models;

import java.time.Instant;

import com.promptvault.repository.entities.PromptVersion;

public record PromptVersionResponse(Long id, Long promptId, Integer versionNumber, String content,
                                    Instant createdAt) {

    public static PromptVersionResponse from(PromptVersion version) {
        return new PromptVersionResponse(version.getId(), version.getPromptId(), version.getVersionNumber(),
                version.getContent(), version.getCreatedAt());
    }
}
