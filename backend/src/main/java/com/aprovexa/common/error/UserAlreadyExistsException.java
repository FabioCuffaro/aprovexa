package com.aprovexa.common.error;

public class UserAlreadyExistsException extends RuntimeException {

    public UserAlreadyExistsException(String email) {
        super("A user with email '%s' already exists".formatted(email));
    }
}
