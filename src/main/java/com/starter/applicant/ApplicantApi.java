package com.starter.applicant;

import com.starter.common.api.PageRes;
import java.time.Instant;

/** Applicant module contract. Other modules inject this interface. */
public interface ApplicantApi {

    record Info(Long id, String name, String email, String status, Instant createdAt) {}

    record Summary(Long id, String name, String status) {}

    Info findById(Long id);

    PageRes<Summary> listActive(int page, int size, String sort, String order);
}
