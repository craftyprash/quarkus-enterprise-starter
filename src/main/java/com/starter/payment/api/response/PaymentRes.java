package com.starter.payment.api.response;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentRes(
        Long id,
        Long drawdownId,
        String bank,
        String transferMode,
        BigDecimal amount,
        String status,
        String bankReference,
        Instant createdAt) {}
