package com.auction.userservice.exception;

public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException(String message) {
        super(
                "User already exists: " + message
        );
    }
}
