package com.promptvault.models;

import jakarta.validation.constraints.Size;

public record PromptUpdateRequest(
        @Size(max = 255) String title,
        String content) {
}
