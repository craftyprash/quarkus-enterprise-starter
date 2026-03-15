package com.starter.applicant.internal;

import com.starter.applicant.ApplicantApi;
import com.starter.applicant.api.request.CreateApplicantReq;
import com.starter.applicant.api.response.ApplicantRes;
import com.starter.applicant.domain.Applicant;
import com.starter.common.exception.DuplicateException;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class ApplicantService implements ApplicantApi {

    private static final Logger log = LoggerFactory.getLogger(ApplicantService.class);

    @Inject ApplicantRepo repo;
    @Inject ApplicantQueryRepo queryRepo;

    @Transactional
    public ApplicantRes create(CreateApplicantReq req) {
        repo.findByEmail(req.email())
                .ifPresent(
                        a -> {
                            throw new DuplicateException("Email already exists");
                        });

        var applicant = new Applicant(req.name(), req.email());
        repo.persist(applicant);

        log.info("Applicant created id={}", applicant.id);
        return new ApplicantRes(
                applicant.id,
                applicant.name,
                applicant.email,
                applicant.status,
                applicant.createdAt);
    }

    @Override
    @CacheResult(cacheName = "applicant-by-id")
    public Info findById(Long id) {
        var a =
                repo.findByIdOptional(id)
                        .orElseThrow(() -> new NoSuchElementException("Applicant not found"));
        return new Info(a.id, a.name, a.email, a.status, a.createdAt);
    }

    @Override
    public List<Summary> listActive() {
        return queryRepo.findActiveSummaries();
    }
}
