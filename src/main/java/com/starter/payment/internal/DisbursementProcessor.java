package com.starter.payment.internal;

import com.starter.common.integration.BankRouter;
import com.starter.common.integration.LmsGateway;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Drains DISBURSE_REQUESTED outbox events. The orchestration method is deliberately NOT
 * {@code @Transactional}: each step claims / finalises in its own short transaction (in {@link
 * PaymentService}), and the bank/LMS remote calls happen <em>between</em> those transactions so a
 * slow bank never holds a DB connection.
 */
@ApplicationScoped
public class DisbursementProcessor {

    private static final Logger log = LoggerFactory.getLogger(DisbursementProcessor.class);

    @Inject PaymentService paymentService;
    @Inject BankRouter bankRouter;
    @Inject LmsGateway lmsGateway;

    @Scheduled(every = "5s", identity = "disbursement-processor")
    public void processPending() {
        for (var eventId : paymentService.claimableDisbursementEventIds()) {
            processOne(eventId);
        }
    }

    void processOne(Long eventId) {
        var task = paymentService.claim(eventId).orElse(null); // tx: claim
        if (task == null) {
            return;
        }
        try {
            var bankRef =
                    bankRouter
                            .resolveByBank(task.bank())
                            .disburse(task.paymentId(), task.amount()); // remote — no tx
            if ("IMPS".equals(task.transferMode())) {
                lmsGateway.recordDisbursement(task.drawdownId(), task.amount(), bankRef); // remote
                paymentService.completeImmediate(
                        eventId, task.paymentId(), bankRef); // tx: finalise
            } else {
                paymentService.markAwaitingSettlement(eventId, task.paymentId(), bankRef); // tx
            }
        } catch (RuntimeException e) {
            log.error("Disbursement failed payment={}", task.paymentId(), e);
            paymentService.failDisbursement(eventId, task.paymentId()); // tx: terminal failure
        }
    }
}
