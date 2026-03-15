package com.starter.payment.domain;

import com.starter.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import java.math.BigDecimal;
import java.util.Objects;
import org.hibernate.envers.Audited;

@Entity
@Audited
public class Payment extends BaseEntity {

    @Column(name = "drawdown_id", nullable = false)
    public Long drawdownId;

    @Column(nullable = false)
    public String bank;

    @Column(name = "transfer_mode", nullable = false)
    public String transferMode;

    @Column(nullable = false, precision = 15, scale = 2)
    public BigDecimal amount;

    @Column(nullable = false)
    public String status = "INITIATED";

    @Column(name = "bank_reference")
    public String bankReference;

    protected Payment() {}

    public Payment(Long drawdownId, String bank, String transferMode, BigDecimal amount) {
        this.drawdownId = Objects.requireNonNull(drawdownId, "drawdownId required");
        this.bank = Objects.requireNonNull(bank, "bank required");
        this.transferMode = Objects.requireNonNull(transferMode, "transferMode required");
        this.amount = Objects.requireNonNull(amount, "amount required");
    }
}
