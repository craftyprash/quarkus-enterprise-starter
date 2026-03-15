package com.starter.drawdown.domain;

import com.starter.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import java.math.BigDecimal;
import java.util.Objects;
import org.hibernate.envers.Audited;

@Entity
@Audited
public class Drawdown extends BaseEntity {

    @Column(name = "applicant_id", nullable = false)
    public Long applicantId;

    @Column(name = "anchor_code", nullable = false)
    public String anchorCode;

    @Column(nullable = false, precision = 15, scale = 2)
    public BigDecimal amount;

    @Column(nullable = false)
    public String status = "PENDING";

    protected Drawdown() {}

    public Drawdown(Long applicantId, String anchorCode, BigDecimal amount) {
        this.applicantId = Objects.requireNonNull(applicantId, "applicantId required");
        this.anchorCode = Objects.requireNonNull(anchorCode, "anchorCode required");
        this.amount = Objects.requireNonNull(amount, "amount required");
    }
}
