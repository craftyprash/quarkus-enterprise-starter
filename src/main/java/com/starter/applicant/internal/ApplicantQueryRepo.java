package com.starter.applicant.internal;

import com.starter.applicant.ApplicantApi.Summary;
import com.starter.common.query.QueryRepo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.Tuple;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@ApplicationScoped
public class ApplicantQueryRepo {

    @Inject QueryRepo queryRepo;

    // Allow-list: API sort field -> real DB column. Only these server-controlled constants may
    // reach the ORDER BY clause — raw user input is NEVER interpolated into SQL.
    private static final Map<String, String> SORT_COLUMNS =
            Map.of("id", "id", "name", "name", "status", "status", "createdAt", "created_at");

    /** Paginated list — safe dynamic ORDER BY via allow-list; LIMIT/OFFSET via bound params. */
    public List<Summary> findActive(int page, int size, String sort, String order) {
        var column = SORT_COLUMNS.getOrDefault(sort, "id");
        var direction = "desc".equalsIgnoreCase(order) ? "DESC" : "ASC";
        var sql =
                "SELECT id, name, status FROM applicant WHERE status = ?1"
                        + " ORDER BY "
                        + column
                        + " "
                        + direction
                        + " LIMIT ?2 OFFSET ?3";
        return queryRepo.sql(sql, "ACTIVE", size, page * size).map(this::toSummary).toList();
    }

    /** Scalar query — total count for pagination metadata. */
    public long countActive() {
        return queryRepo
                .sql("SELECT count(*) FROM applicant WHERE status = ?1", "ACTIVE")
                .single(Number.class)
                .longValue();
    }

    /** Single row query — named params, .first() returns Optional<T>. */
    public Optional<Summary> findSummaryByEmail(String email) {
        return queryRepo
                .sql("SELECT id, name, status FROM applicant WHERE email = :email")
                .param("email", email)
                .map(this::toSummary)
                .first();
    }

    private Summary toSummary(Tuple t) {
        return new Summary(
                t.get("id", Long.class),
                t.get("name", String.class),
                t.get("status", String.class));
    }
}
