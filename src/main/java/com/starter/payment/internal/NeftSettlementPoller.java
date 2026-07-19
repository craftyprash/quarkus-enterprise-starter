package com.starter.payment.internal;

import com.starter.common.integration.BankGateway;
import com.starter.common.integration.LmsGateway;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Polls the bank for NEFT payments left in POLLING by {@link DisbursementProcessor}. Same
 * discipline: the status check and LMS record are remote calls made outside any transaction; only
 * the terminal status write is transactional.
 */
@ApplicationScoped
public class NeftSettlementPoller {

    private static final Logger log = LoggerFactory.getLogger(NeftSettlementPoller.class);

    @Inject PaymentService paymentService;
    @Inject BankGateway bankGateway;
    @Inject LmsGateway lmsGateway;

    @Scheduled(every = "30s", identity = "neft-settlement-poller")
    public void pollSettlements() {
        for (var task : paymentService.pollingTasks()) {
            pollOne(task);
        }
    }

    void pollOne(PaymentService.PollTask task) {
        try {
            var status = bankGateway.checkStatus(task.bankReference()); // remote — no tx
            switch (status) {
                case "SETTLED" -> {
                    lmsGateway.recordDisbursement(
                            task.drawdownId(), task.amount(), task.bankReference()); // remote
                    paymentService.markSettled(task.paymentId()); // tx
                }
                case "FAILED" -> paymentService.failSettlement(task.paymentId()); // tx
                default -> log.debug("NEFT still pending payment={}", task.paymentId());
            }
        } catch (RuntimeException e) {
            log.error("Settlement poll failed payment={}", task.paymentId(), e);
        }
    }
}
