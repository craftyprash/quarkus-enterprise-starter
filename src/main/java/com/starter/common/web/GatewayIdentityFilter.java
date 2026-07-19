package com.starter.common.web;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * Populates {@link CallerContext} from the gateway-provided {@code X-Anchor-Scope} header. This is
 * NOT authentication (the gateway does that) — it reads validated metadata the gateway forwards and
 * makes it available for record-level checks. Absent/blank header → empty scope (deny by default).
 */
@Provider
@ApplicationScoped
public class GatewayIdentityFilter implements ContainerRequestFilter {

    static final String ANCHOR_SCOPE_HEADER = "X-Anchor-Scope";

    @Inject CallerContext callerContext;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        var header = requestContext.getHeaderString(ANCHOR_SCOPE_HEADER);
        if (header != null && !header.isBlank()) {
            var scope =
                    Arrays.stream(header.split(","))
                            .map(String::trim)
                            .filter(s -> !s.isBlank())
                            .collect(Collectors.toUnmodifiableSet());
            callerContext.setAnchorScope(scope);
        }
    }
}
