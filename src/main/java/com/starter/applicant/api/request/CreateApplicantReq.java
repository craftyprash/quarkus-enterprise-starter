package com.starter.applicant.api.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record CreateApplicantReq(@NotBlank String name, @NotBlank @Email String email) {}
