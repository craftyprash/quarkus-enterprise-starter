package com.starter.common.security;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.Path;
import java.util.Map;
import java.util.Set;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

/** REST client for the external permission service. Passes the JWT token as-is. */
@RegisterRestClient(configKey = "permission-api")
@Path("/permissions")
public interface PermissionClient {

    record PermissionResponse(
            Map<String, Set<String>> permissions, Map<String, Set<String>> scopes) {}

    @GET
    PermissionResponse load(@HeaderParam("Authorization") String authHeader);
}
