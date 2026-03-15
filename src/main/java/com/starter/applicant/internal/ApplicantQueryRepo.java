package com.starter.applicant.internal;

import com.starter.applicant.ApplicantApi.Summary;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import java.util.List;

@ApplicationScoped
public class ApplicantQueryRepo {

    @Inject EntityManager em;

    public List<Summary> findActiveSummaries() {
        var sql = "SELECT id, name, status FROM applicant WHERE status = ?1";
        List<Tuple> rows =
                em.createNativeQuery(sql, Tuple.class).setParameter(1, "ACTIVE").getResultList();
        return rows.stream()
                .map(
                        t ->
                                new Summary(
                                        t.get(0, Long.class),
                                        t.get(1, String.class),
                                        t.get(2, String.class)))
                .toList();
    }
}
