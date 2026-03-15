package com.starter.common.query;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.List;

/**
 * Thin wrapper for native SQL queries with typed Tuple results. Use this when Panache repository
 * methods are insufficient — complex joins, aggregations, or custom projections.
 */
@ApplicationScoped
public class QueryRepo {

    @Inject EntityManager em;

    /** Execute a native SQL query returning typed Tuple rows. */
    @SuppressWarnings("unchecked")
    public List<Tuple> query(String sql, Object... params) {
        var q = em.createNativeQuery(sql, Tuple.class);
        for (int i = 0; i < params.length; i++) {
            q.setParameter(i + 1, params[i]);
        }
        return q.getResultList();
    }

    /** Execute a native SQL query returning a single scalar result. */
    @SuppressWarnings("unchecked")
    public <T> T scalar(String sql, Class<T> type, Object... params) {
        var q = em.createNativeQuery(sql);
        for (int i = 0; i < params.length; i++) {
            q.setParameter(i + 1, params[i]);
        }
        return type.cast(q.getSingleResult());
    }
}
