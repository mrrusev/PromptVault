package com.promptvault.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PromptCreateRequest(
        @NotNull Long collectionId,
        @NotBlank @Size(max = 255) String title,
        @NotNull String content) {
}
