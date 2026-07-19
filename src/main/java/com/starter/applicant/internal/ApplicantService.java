package com.starter.applicant.internal;

import com.starter.applicant.ApplicantApi;
import com.starter.applicant.api.request.CreateApplicantReq;
import com.starter.applicant.api.response.ApplicantRes;
import com.starter.applicant.domain.Applicant;
import com.starter.common.api.PageRes;
import com.starter.common.exception.DuplicateException;
import com.starter.common.exception.NotFoundException;
import io.quarkus.cache.CacheResult;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import jakarta.validation.constraints.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class ApplicantService implements ApplicantApi {

    private static final Logger log = LoggerFactory.getLogger(ApplicantService.class);

    @Inject ApplicantRepo repo;
    @Inject ApplicantQueryRepo queryRepo;
    @Inject Validator validator;

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
                        .orElseThrow(() -> new NotFoundException("Applicant not found"));
        return new Info(a.id, a.name, a.email, a.status, a.createdAt);
    }

    @Override
    public PageRes<Summary> listActive(int page, int size, String sort, String order) {
        validateSort(sort, order);
        var content = queryRepo.findActive(page, size, sort, order);
        var total = queryRepo.countActive();
        return PageRes.of(content, page, size, total);
    }

    // Programmatic field validation: reuse Bean Validation for values that arrive as loose query
    // params against the sortable allow-list. Throws ConstraintViolationException, which
    // GlobalExceptionMapper renders as a 400 with field-level errors — same shape as @Valid.
    private void validateSort(String sort, String order) {
        var violations = validator.validate(new SortCriteria(sort, order));
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }

    // Keep the pattern in sync with ApplicantQueryRepo.SORT_COLUMNS.
    private record SortCriteria(
            @Pattern(
                            regexp = "id|name|status|createdAt",
                            message = "must be one of: id, name, status, createdAt")
                    String sort,
            @Pattern(regexp = "asc|desc", message = "must be 'asc' or 'desc'") String order) {}
}
