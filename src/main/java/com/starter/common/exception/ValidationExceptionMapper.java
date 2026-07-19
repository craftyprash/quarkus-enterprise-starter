package com.starter.common.exception;

import io.quarkus.hibernate.validator.runtime.jaxrs.ResteasyReactiveViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Overrides Quarkus REST's built-in validation response so bean-validation failures use the same
 * error shape as everything else. Registered for the exact framework type, so it wins over the
 * built-in mapper; the response is built by {@link GlobalExceptionMapper}.
 */
@Provider
public class ValidationExceptionMapper
        implements ExceptionMapper<ResteasyReactiveViolationException> {

    @Override
    public Response toResponse(ResteasyReactiveViolationException ex) {
        return GlobalExceptionMapper.validationResponse(ex);
    }
}
