package com.promptvault.models;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CollectionRequest(@NotBlank @Size(min = 1, max = 255) String name) {
}
