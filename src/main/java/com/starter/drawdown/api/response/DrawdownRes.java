package com.starter.drawdown.api.response;

import java.math.BigDecimal;
import java.time.Instant;

public record DrawdownRes(
        Long id,
        Long applicantId,
        String applicantName,
        String anchorCode,
        BigDecimal amount,
        String status,
        Instant createdAt) {}
