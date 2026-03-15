package com.starter.applicant.internal;

import com.starter.applicant.domain.Applicant;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.Optional;

@ApplicationScoped
public class ApplicantRepo implements PanacheRepository<Applicant> {

    public Optional<Applicant> findByEmail(String email) {
        return find("email", email).firstResultOptional();
    }
}
