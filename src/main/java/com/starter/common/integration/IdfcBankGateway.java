package com.starter.common.integration;

import jakarta.enterprise.context.ApplicationScoped;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * IDFC bank gateway — in-process mock (IMPS rail). A real impl injects a
 * {@code @RegisterRestClient} client.
 */
@ApplicationScoped
public class IdfcBankGateway implements BankGateway {

    private static final Logger log = LoggerFactory.getLogger(IdfcBankGateway.class);

    @Override
    public String bankCode() {
        return "IDFC";
    }

    @Override
    public String transferMode(String anchorCode) {
        return "IMPS";
    }

    @Override
    public String disburse(Long paymentId, BigDecimal amount) {
        var bankReference = "IDFC-" + paymentId; // deterministic on payment id → idempotent
        log.info("IDFC disburse payment={} ref={}", paymentId, bankReference);
        return bankReference;
    }

    @Override
    public String checkStatus(String bankReference) {
        return "SETTLED";
    }
}
