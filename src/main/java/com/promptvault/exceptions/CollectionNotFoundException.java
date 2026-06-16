package com.promptvault.exceptions;

public class CollectionNotFoundException extends RuntimeException {

    public CollectionNotFoundException(Long id) {
        super("Collection not found: " + id);
    }
}
