package com.starter.common.exception;

/**
 * A request is well-formed but violates a business/semantic rule (e.g. amount exceeds a sanctioned
 * limit) — maps to 422. Use this, not {@code IllegalArgumentException}: the latter signals a
 * method-contract/programming error and is deliberately left unmapped (→ 500). For field-shape
 * problems use Bean Validation (→ 400) instead.
 */
public class BusinessValidationException extends RuntimeException {

    public BusinessValidationException(String message) {
        super(message);
    }
}
