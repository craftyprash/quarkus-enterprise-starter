package com.starter.applicant.internal;

import com.starter.applicant.ApplicantApi.Summary;
import com.starter.common.query.QueryRepo;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

@ApplicationScoped
public class ApplicantQueryRepo {

    @Inject QueryRepo queryRepo;

    public List<Summary> findActiveSummaries() {
        var sql = "SELECT id, name, status FROM applicant WHERE status = ?1";
        return queryRepo.query(sql, "ACTIVE").stream()
                .map(
                        t ->
                                new Summary(
                                        t.get(0, Long.class),
                                        t.get(1, String.class),
                                        t.get(2, String.class)))
                .toList();
    }
}
