package com.starter.common.api;

import java.util.List;

/**
 * Standard paginated response shape: {@code {content, page, size, totalElements, totalPages}}.
 *
 * <p>Returned directly (no success envelope) — see CLAUDE.md §4. Use for any list endpoint that
 * pages.
 */
public record PageRes<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <T> PageRes<T> of(List<T> content, int page, int size, long totalElements) {
        var totalPages = size > 0 ? (int) Math.ceil((double) totalElements / size) : 0;
        return new PageRes<>(content, page, size, totalElements, totalPages);
    }
}
