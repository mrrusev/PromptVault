package com.promptvault.exceptions;

public class PromptNotFoundException extends RuntimeException {

    public PromptNotFoundException(Long id) {
        super("Prompt not found: " + id);
    }
}
