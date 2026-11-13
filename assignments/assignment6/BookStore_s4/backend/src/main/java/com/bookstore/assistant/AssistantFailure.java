package com.bookstore.assistant;

import org.springframework.http.HttpStatus;

public class AssistantFailure extends RuntimeException {
    private final HttpStatus status;
    public AssistantFailure(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }
    public HttpStatus status() { return status; }
}
