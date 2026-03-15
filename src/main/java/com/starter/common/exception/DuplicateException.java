package com.starter.common.exception;

/** Business rule violation: a unique constraint would be broken. */
public class DuplicateException extends RuntimeException {

    public DuplicateException(String message) {
        super(message);
    }
}
