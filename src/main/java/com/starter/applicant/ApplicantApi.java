package com.starter.applicant;

import java.time.Instant;
import java.util.List;

/** Applicant module contract. Other modules inject this interface. */
public interface ApplicantApi {

    record Info(Long id, String name, String email, String status, Instant createdAt) {}

    record Summary(Long id, String name, String status) {}

    Info findById(Long id);

    List<Summary> listActive();
}
