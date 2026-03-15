package com.starter.common.security;

import jakarta.annotation.Priority;
import jakarta.inject.Inject;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.ext.Provider;
import java.util.Map;
import org.eclipse.microprofile.jwt.JsonWebToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Provider
@Priority(Priorities.AUTHORIZATION)
public class PermissionFilter implements ContainerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(PermissionFilter.class);

    @Inject JsonWebToken jwt;
    @Inject PermissionService permissionService;
    @Inject PermissionContext context;

    @Override
    public void filter(ContainerRequestContext requestContext) {
        if (jwt.getRawToken() == null) {
            return;
        }

        var subject = jwt.getSubject();
        if (subject == null) {
            return;
        }

        try {
            var authHeader = "Bearer " + jwt.getRawToken();
            var response = permissionService.loadPermissions(authHeader);

            context.setUserId(Long.parseLong(subject));
            context.setPermissions(
                    response.permissions() != null ? response.permissions() : Map.of());
            context.setScopes(response.scopes() != null ? response.scopes() : Map.of());
        } catch (Exception e) {
            log.warn("Failed to load permissions for user={}", subject, e);
        }
    }
}
