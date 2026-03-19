package com.starter.applicant.domain;

import com.starter.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import java.util.Objects;

@Entity
public class Applicant extends BaseEntity {

    @Column(nullable = false)
    public String name;

    @Column(nullable = false, unique = true)
    public String email;

    @Column(nullable = false)
    public String status = "ACTIVE";

    protected Applicant() {}

    public Applicant(String name, String email) {
        this.name = Objects.requireNonNull(name, "name required");
        this.email = Objects.requireNonNull(email, "email required");
    }
}
