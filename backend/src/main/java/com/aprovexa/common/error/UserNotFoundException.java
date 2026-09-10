package com.aprovexa.common.error;

public class UserNotFoundException extends RuntimeException {

    public UserNotFoundException(String email) {
        super("User with email '%s' was not found".formatted(email));
    }
}
