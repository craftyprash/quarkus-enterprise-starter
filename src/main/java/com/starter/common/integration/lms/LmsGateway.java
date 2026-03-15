package com.starter.common.integration.lms;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.math.BigDecimal;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class LmsGateway {

    private static final Logger log = LoggerFactory.getLogger(LmsGateway.class);

    @Inject @RestClient LmsClient client;

    public void recordDisbursement(Long drawdownId, BigDecimal amount, String bankReference) {
        var response =
                client.recordDisbursement(
                        new LmsClient.DisbursementRecord(drawdownId, amount, bankReference));
        log.info(
                "LMS recordDisbursement drawdown={} ref={} status={}",
                drawdownId,
                bankReference,
                response.status());
    }
}
