package com.starter.payment.internal;

import com.starter.common.integration.bank.BankRouter;
import com.starter.common.integration.lms.LmsGateway;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class NeftSettlementPoller {

    private static final Logger log = LoggerFactory.getLogger(NeftSettlementPoller.class);

    @Inject PaymentRepo paymentRepo;
    @Inject BankRouter bankRouter;
    @Inject LmsGateway lmsGateway;

    @Scheduled(every = "30s", identity = "neft-settlement-poller")
    @Transactional
    public void pollSettlements() {
        var polling = paymentRepo.findByStatus("POLLING");

        for (var payment : polling) {
            try {
                var gateway = bankRouter.resolveByBank(payment.bank);
                var bankStatus = gateway.checkStatus(payment.bankReference);

                switch (bankStatus) {
                    case "SETTLED" -> {
                        payment.status = "DISBURSED";
                        lmsGateway.recordDisbursement(
                                payment.drawdownId, payment.amount, payment.bankReference);
                        log.info(
                                "NEFT settled payment={} ref={}",
                                payment.id,
                                payment.bankReference);
                    }
                    case "FAILED" -> {
                        payment.status = "FAILED";
                        log.warn(
                                "NEFT failed payment={} ref={}", payment.id, payment.bankReference);
                    }
                    default ->
                            log.debug(
                                    "NEFT still pending payment={} ref={}",
                                    payment.id,
                                    payment.bankReference);
                }
            } catch (Exception e) {
                log.error("Settlement poll failed payment={}", payment.id, e);
            }
        }
    }
}
