package com.starter.applicant.internal;

import com.starter.applicant.ApplicantApi.Summary;
import com.starter.common.query.QueryRepo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.Tuple;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class ApplicantQueryRepo {

    @Inject QueryRepo queryRepo;

    /** List query — positional params, .map(mapper).toList() */
    public List<Summary> findActiveSummaries() {
        return queryRepo
                .sql("SELECT id, name, status FROM applicant WHERE status = ?1", "ACTIVE")
                .map(this::toSummary)
                .toList();
    }

    /** Single row query — named params, .map(mapper).first() returns Optional<T> */
    public Optional<Summary> findSummaryByEmail(String email) {
        return queryRepo
                .sql("SELECT id, name, status FROM applicant WHERE email = :email")
                .param("email", email)
                .map(this::toSummary)
                .first();
    }

    /** Scalar query — positional params, .single(Type.class) for counts, sums, etc. */
    public long countActive() {
        return queryRepo
                .sql("SELECT count(*) FROM applicant WHERE status = ?1", "ACTIVE")
                .single(Long.class);
    }

    private Summary toSummary(Tuple t) {
        return new Summary(
                t.get("id", Long.class),
                t.get("name", String.class),
                t.get("status", String.class));
    }
}
