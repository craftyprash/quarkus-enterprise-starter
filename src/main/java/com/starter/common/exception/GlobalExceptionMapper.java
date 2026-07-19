package com.starter.common.exception;

import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Single source of error responses. Shape follows the org REST guide:
 *
 * <pre>{ "status": "error", "message": "...", "errors": [ { "field": "...", "message": "..." } ] }
 * </pre>
 *
 * <p>{@code errors} carries field-level detail for validation failures; it is empty for everything
 * else. The HTTP status code conveys the category. Never build error JSON anywhere else — bean
 * validation is routed here by {@link ValidationExceptionMapper}.
 */
@Provider
public class GlobalExceptionMapper implements ExceptionMapper<Exception> {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionMapper.class);

    public record FieldErrorRes(String field, String message) {}

    public record ErrorRes(String status, String message, List<FieldErrorRes> errors) {}

    @Override
    public Response toResponse(Exception ex) {
        return switch (ex) {
            case ForbiddenException e -> respond(403, e.getMessage());
            case NotFoundException e -> respond(404, e.getMessage());
            case BusinessValidationException e -> respond(422, e.getMessage());
            case IllegalStateException e -> respond(409, e.getMessage());
            case DuplicateException e -> respond(409, e.getMessage());
            case ConstraintViolationException e -> validationResponse(e);
            // IllegalArgumentException, NullPointerException, etc. are contract/programming errors:
            // intentionally NOT mapped, so they surface as 500 (logged) rather than a client 4xx.
            default -> {
                log.error("Unhandled exception", ex);
                yield respond(500, "Internal server error");
            }
        };
    }

    private static Response respond(int status, String message) {
        return Response.status(status).entity(new ErrorRes("error", message, List.of())).build();
    }

    /** Field-level validation response — shared with {@link ValidationExceptionMapper}. */
    static Response validationResponse(ConstraintViolationException ex) {
        var fieldErrors =
                ex.getConstraintViolations().stream()
                        .map(
                                v ->
                                        new FieldErrorRes(
                                                leafField(v.getPropertyPath().toString()),
                                                v.getMessage()))
                        .toList();
        return Response.status(400)
                .entity(new ErrorRes("error", "Validation failed", fieldErrors))
                .build();
    }

    // Bean-validation property paths look like "create.req.email" — expose just the field name.
    private static String leafField(String propertyPath) {
        var dot = propertyPath.lastIndexOf('.');
        return dot >= 0 ? propertyPath.substring(dot + 1) : propertyPath;
    }
}
