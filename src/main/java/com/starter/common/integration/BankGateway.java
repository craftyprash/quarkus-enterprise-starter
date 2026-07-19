package com.starter.common.integration;

import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Gateway wrapper for the disbursing bank. This is an in-process MOCK for the starter.
 *
 * <p>A real implementation injects a {@code @RegisterRestClient} bank client (see CLAUDE.md §12),
 * configures a timeout, and never trusts the upstream response blindly. It is called only from the
 * outbox processors — never inside a {@code @Transactional} method.
 */
@ApplicationScoped
public class BankGateway {

    private static final Logger log = LoggerFactory.getLogger(BankGateway.class);

    /**
     * Disburse to the bank and return its reference. The reference is derived from the payment id
     * so a retried disbursement returns the same reference — i.e. the call is idempotent.
     */
    public String disburse(Long paymentId, BigDecimal amount) {
        var bankReference = "BANKREF-" + paymentId;
        log.info("Bank disburse payment={} ref={}", paymentId, bankReference);
        return bankReference;
    }

    /** Check settlement status for a NEFT transfer. Mock always settles. */
    public String checkStatus(String bankReference) {
        return "SETTLED";
    }
}
