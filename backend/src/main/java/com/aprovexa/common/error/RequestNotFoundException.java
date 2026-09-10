package com.aprovexa.common.error;

public class RequestNotFoundException extends RuntimeException {

    public RequestNotFoundException(Long requestId) {
        super("Request with id %d was not found".formatted(requestId));
    }
}
