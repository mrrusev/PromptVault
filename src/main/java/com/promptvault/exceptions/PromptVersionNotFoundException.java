package com.promptvault.exceptions;

public class PromptVersionNotFoundException extends RuntimeException {

    public PromptVersionNotFoundException(Integer versionNumber) {
        super("Prompt version not found: " + versionNumber);
    }
}
