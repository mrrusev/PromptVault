package com.promptvault.models;

public record DashboardResponse(
        long totalCollections,
        long totalPrompts,
        long totalVersions,
        PromptResponse latestPrompt) {
}
