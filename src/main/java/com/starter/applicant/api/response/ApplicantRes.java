package com.starter.applicant.api.response;

import java.time.Instant;

public record ApplicantRes(Long id, String name, String email, String status, Instant createdAt) {}
