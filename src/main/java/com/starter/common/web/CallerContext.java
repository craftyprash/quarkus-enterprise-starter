package com.starter.common.web;

import jakarta.enterprise.context.RequestScoped;
import java.util.Set;

/**
 * Caller scope forwarded by the API gateway (APISix) as <em>untrusted</em> request metadata. The
 * gateway authenticates; the app still enforces record-level access using this validated scope,
 * never an id taken from the path/body (CLAUDE.md §5).
 */
@RequestScoped
public class CallerContext {

    private Set<String> anchorScope = Set.of();

    public Set<String> anchorScope() {
        return anchorScope;
    }

    public void setAnchorScope(Set<String> anchorScope) {
        this.anchorScope = anchorScope;
    }

    /** True if the caller is scoped to this anchor. Empty scope denies by default. */
    public boolean mayAccessAnchor(String anchorCode) {
        return anchorScope.contains(anchorCode);
    }
}
