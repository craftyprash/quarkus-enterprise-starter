package com.starter.drawdown;

import java.math.BigDecimal;
import java.time.Instant;

/** Drawdown module contract. Other modules inject this interface. */
public interface DrawdownApi {

    record Info(
            Long id,
            Long applicantId,
            String applicantName,
            String anchorCode,
            BigDecimal amount,
            String status,
            Instant createdAt) {}

    record CreateInput(Long applicantId, String anchorCode, BigDecimal amount) {}

    Info create(CreateInput input);

    Info findById(Long id);

    Info disburse(Long id);
}
