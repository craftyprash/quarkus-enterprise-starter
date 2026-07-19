package com.starter.common.exception;

import io.quarkus.hibernate.validator.runtime.jaxrs.ResteasyReactiveViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Overrides Quarkus REST's built-in validation response so bean-validation failures use the same
 * error shape as everything else. The response is built by {@link GlobalExceptionMapper}.
 *
 * <p>This must be a separate mapper for the <em>exact</em> framework type — {@code @Priority} on
 * {@code GlobalExceptionMapper}'s {@code ExceptionMapper<Exception>} cannot replace it. JAX-RS
 * selects a mapper by nearest-supertype first; priority only breaks ties among mappers of the
 * <em>same</em> type. A user mapper registered for this exact type wins over the built-in one.
 */
@Provider
public class ValidationExceptionMapper
        implements ExceptionMapper<ResteasyReactiveViolationException> {

    @Override
    public Response toResponse(ResteasyReactiveViolationException ex) {
        return GlobalExceptionMapper.validationResponse(ex);
    }
}
