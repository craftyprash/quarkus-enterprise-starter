package com.starter.common.query;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

/**
 * Fluent wrapper for native SQL queries with typed Tuple results. Use this when Panache repository
 * methods are insufficient — complex joins, aggregations, or custom projections.
 *
 * <p>Two parameter styles:
 *
 * <pre>
 * // positional — simple queries
 * queryRepo.sql("SELECT ... WHERE status = ?1", "ACTIVE")
 *          .map(this::toRow).toList();
 *
 * // named — multi-param queries
 * queryRepo.sql("SELECT ... WHERE status = :status AND region = :region")
 *          .param("status", "ACTIVE")
 *          .param("region", "NORTH")
 *          .map(this::toRow).toList();
 * </pre>
 */
@ApplicationScoped
public class QueryRepo {

    @Inject EntityManager em;

    /** Start a query with positional parameters (?1, ?2, ...). */
    public Query sql(String sql, Object... params) {
        return new Query(em, sql, params);
    }

    public static class Query {

        private final EntityManager em;
        private final String sql;
        private final Object[] positionalParams;
        private final Map<String, Object> namedParams = new HashMap<>();

        Query(EntityManager em, String sql, Object[] positionalParams) {
            this.em = em;
            this.sql = sql;
            this.positionalParams = positionalParams;
        }

        /** Add a named parameter (:name style). */
        public Query param(String name, Object value) {
            namedParams.put(name, value);
            return this;
        }

        /** Map each row to a typed result. Chain with toList() or first(). */
        public <T> MappedQuery<T> map(Function<Tuple, T> mapper) {
            return new MappedQuery<>(execute(), mapper);
        }

        /** Return a single scalar value. */
        public <T> T single(Class<T> type) {
            var q = em.createNativeQuery(sql);
            bindParams(q);
            return type.cast(q.getSingleResult());
        }

        @SuppressWarnings("unchecked")
        private List<Tuple> execute() {
            var q = em.createNativeQuery(sql, Tuple.class);
            bindParams(q);
            return q.getResultList();
        }

        private void bindParams(jakarta.persistence.Query q) {
            if (!namedParams.isEmpty()) {
                namedParams.forEach(q::setParameter);
            } else {
                for (int i = 0; i < positionalParams.length; i++) {
                    q.setParameter(i + 1, positionalParams[i]);
                }
            }
        }
    }

    public static class MappedQuery<T> {

        private final List<Tuple> rows;
        private final Function<Tuple, T> mapper;

        MappedQuery(List<Tuple> rows, Function<Tuple, T> mapper) {
            this.rows = rows;
            this.mapper = mapper;
        }

        public List<T> toList() {
            return rows.stream().map(mapper).toList();
        }

        public Optional<T> first() {
            return rows.stream().map(mapper).findFirst();
        }
    }
}
