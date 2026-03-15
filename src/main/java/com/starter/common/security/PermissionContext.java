package com.starter.common.security;

import jakarta.enterprise.context.RequestScoped;
import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/** Request-scoped holder for the authenticated user's permissions and data scopes. */
@RequestScoped
public class PermissionContext {

    private Long userId;
    private Map<String, Set<String>> permissions = Map.of();
    private Map<String, Set<String>> scopes = Map.of();

    public boolean has(String resource, String action) {
        var actions = permissions.get(resource);
        return actions != null && actions.contains(action);
    }

    public boolean hasAny(String resource, String... actions) {
        var allowed = permissions.get(resource);
        if (allowed == null) return false;
        return Arrays.stream(actions).anyMatch(allowed::contains);
    }

    public Set<String> scope(String key) {
        return scopes.getOrDefault(key, Set.of());
    }

    public Long userId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public void setPermissions(Map<String, Set<String>> permissions) {
        this.permissions = permissions;
    }

    public void setScopes(Map<String, Set<String>> scopes) {
        this.scopes = scopes;
    }
}
