package com.starter.common.exception;

/**
 * A requested entity does not exist — maps to 404. Throw this (not JDK {@code
 * NoSuchElementException}) so a genuine bug (empty collection, misused iterator) surfaces as a 500
 * instead of being laundered into a client-facing 404.
 */
public class NotFoundException extends RuntimeException {

    public NotFoundException(String message) {
        super(message);
    }
}
