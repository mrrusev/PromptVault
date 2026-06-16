package com.promptvault.models;

import java.time.Instant;

public record CollectionResponse(Long id, String name, Long ownerId, Instant createdAt) {
}
