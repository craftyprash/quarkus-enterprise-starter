package com.starter.common.integration;

import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * HDFC bank gateway — in-process mock (NEFT rail). A real impl injects a
 * {@code @RegisterRestClient} client.
 */
@ApplicationScoped
public class HdfcBankGateway implements BankGateway {

    private static final Logger log = LoggerFactory.getLogger(HdfcBankGateway.class);

    @Override
    public String bankCode() {
        return "HDFC";
    }

    @Override
    public String transferMode(String anchorCode) {
        return "NEFT";
    }

    @Override
    public String disburse(Long paymentId, BigDecimal amount) {
        var bankReference = "HDFC-" + paymentId; // deterministic on payment id → idempotent
        log.info("HDFC disburse payment={} ref={}", paymentId, bankReference);
        return bankReference;
    }

    @Override
    public String checkStatus(String bankReference) {
        return "SETTLED";
    }
}
