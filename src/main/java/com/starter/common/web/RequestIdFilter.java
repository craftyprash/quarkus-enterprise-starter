package com.starter.common.web;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ContainerResponseContext;
import jakarta.ws.rs.container.ContainerResponseFilter;
import jakarta.ws.rs.ext.Provider;
import java.util.UUID;
import org.slf4j.MDC;

/**
 * Puts a request id into the logging MDC (so every log line for a request is correlatable) and
 * echoes it back in the response. Honours a gateway-supplied {@code X-Request-Id}; otherwise
 * generates one. OpenTelemetry adds {@code traceId}/{@code spanId} alongside it.
 */
@Provider
@ApplicationScoped
public class RequestIdFilter implements ContainerRequestFilter, ContainerResponseFilter {

    public static final String REQUEST_ID_HEADER = "X-Request-Id";
    private static final String MDC_KEY = "requestId";

    @Override
    public void filter(ContainerRequestContext request) {
        var id = request.getHeaderString(REQUEST_ID_HEADER);
        if (id == null || id.isBlank()) {
            id = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, id);
        request.setProperty(MDC_KEY, id);
    }

    @Override
    public void filter(ContainerRequestContext request, ContainerResponseContext response) {
        var id = (String) request.getProperty(MDC_KEY);
        if (id != null) {
            response.getHeaders().putSingle(REQUEST_ID_HEADER, id);
        }
        MDC.remove(MDC_KEY); // don't leak the id onto a pooled thread's next request
    }
}
