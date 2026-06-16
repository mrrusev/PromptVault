package com.promptvault.exceptions;

import java.util.HashMap;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;


@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(WebExchangeBindException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidation(WebExchangeBindException ex) {
        Map<String, String> fields = new HashMap<>();
        ex.getFieldErrors().forEach(error ->
                fields.put(error.getField(), error.getDefaultMessage()));
        Map<String, Object> body = new HashMap<>();
        body.put("error", "Validation failed");
        body.put("fields", fields);
        return body;
    }

    @ExceptionHandler(DuplicateUsernameException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public Map<String, String> handleDuplicate(DuplicateUsernameException ex) {
        return Map.of("error", "Username already taken");
    }

    @ExceptionHandler(BadCredentialsException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public Map<String, String> handleBadCredentials(BadCredentialsException ex) {
        return Map.of("error", "Invalid credentials");
    }
}
