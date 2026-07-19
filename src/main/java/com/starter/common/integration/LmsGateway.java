package com.starter.common.integration;

import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gateway wrapper for the Loan Management System. In-process STUB for the starter.
 *
 * <p>A real implementation calls the LMS over HTTP via a {@code @RegisterRestClient} client. Like
 * {@link BankGateway}, it is invoked only from the outbox processors, outside any transaction.
 */
@ApplicationScoped
public class LmsGateway {

    private static final Logger log = LoggerFactory.getLogger(LmsGateway.class);

    public void recordDisbursement(Long drawdownId, BigDecimal amount, String bankReference) {
        log.info("LMS record disbursement drawdown={} ref={}", drawdownId, bankReference);
    }
}
