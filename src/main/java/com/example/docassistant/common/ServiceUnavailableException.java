package com.example.docassistant.common;

/** A downstream dependency (e.g. the AI provider) is unavailable. Maps to HTTP 503. */
public class ServiceUnavailableException extends RuntimeException {

    public ServiceUnavailableException(String message) {
        super(message);
    }

    public ServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
