package com.starter.payment;

import java.math.BigDecimal;
import java.time.Instant;

/** Payment module contract. Handles disbursement via bank integrations. */
public interface PaymentApi {

    record Info(
            Long id,
            Long drawdownId,
            String bank,
            String transferMode,
            BigDecimal amount,
            String status,
            String bankReference,
            Instant createdAt) {}

    record InitiateInput(Long drawdownId, String anchorCode, BigDecimal amount) {}

    Info initiate(InitiateInput input);

    Info findById(Long id);
}
