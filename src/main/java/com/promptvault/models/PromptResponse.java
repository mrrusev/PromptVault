package com.promptvault.models;

import java.time.Instant;

public record PromptResponse(Long id, String title, String content, Long collectionId, Long ownerId,
                             Instant createdAt) {
}
