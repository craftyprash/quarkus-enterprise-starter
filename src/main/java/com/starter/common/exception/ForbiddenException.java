package com.starter.common.exception;

/** Authorization failure: user lacks required permission or data scope. */
public class ForbiddenException extends RuntimeException {

    public ForbiddenException(String message) {
        super(message);
    }
}
