package com.starter.common.security;

import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class PermissionService {

    private static final Logger log = LoggerFactory.getLogger(PermissionService.class);

    @Inject @RestClient PermissionClient client;

    @CacheResult(cacheName = "permissions")
    public PermissionClient.PermissionResponse loadPermissions(String authHeader) {
        log.debug("Loading permissions from external service");
        return client.load(authHeader);
    }
}
