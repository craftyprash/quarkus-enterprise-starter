package com.starter.common.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionMapper.class);

    public record ErrorRes(int status, String error, String message) {}

    @Override
    public Response toResponse(Exception ex) {
        return switch (ex) {
            case ForbiddenException e -> respond(403, "FORBIDDEN", e.getMessage());
            case NoSuchElementException e -> respond(404, "NOT_FOUND", e.getMessage());
            case IllegalArgumentException e -> respond(422, "INVALID_INPUT", e.getMessage());
            case IllegalStateException e -> respond(409, "CONFLICT", e.getMessage());
            case DuplicateException e -> respond(409, "DUPLICATE", e.getMessage());
            case ConstraintViolationException e ->
                    respond(400, "VALIDATION_FAILED", e.getMessage());
            default -> {
                log.error("Unhandled exception", ex);
                yield respond(500, "INTERNAL_ERROR", "Internal server error");
            }
        };
    }

    private Response respond(int status, String error, String message) {
        return Response.status(status).entity(new ErrorRes(status, error, message)).build();
    }
}
