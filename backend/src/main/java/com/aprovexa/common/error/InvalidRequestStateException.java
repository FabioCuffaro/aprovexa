package com.aprovexa.common.error;

public class InvalidRequestStateException extends RuntimeException {

    public InvalidRequestStateException(String message) {
        super(message);
    }
}
