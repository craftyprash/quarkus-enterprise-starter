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
public class DisbursementProcessor {

    private static final Logger log = LoggerFactory.getLogger(DisbursementProcessor.class);

    @Inject OutboxRepo outboxRepo;
    @Inject PaymentRepo paymentRepo;
    @Inject BankRouter bankRouter;
    @Inject LmsGateway lmsGateway;

    @Scheduled(every = "5s", identity = "disbursement-processor")
    @Transactional
    public void processOutbox() {
        var pending = outboxRepo.findPending("DISBURSE_REQUESTED");

        for (var event : pending) {
            var payment = paymentRepo.findByIdOptional(event.aggregateId).orElse(null);

            if (payment == null) {
                event.status = "SKIPPED";
                log.warn(
                        "Outbox event {} references missing payment {}",
                        event.id,
                        event.aggregateId);
                continue;
            }

            try {
                var gateway = bankRouter.resolveByBank(payment.bank);
                var bankRef = gateway.disburse(payment.id, payment.amount);
                payment.bankReference = bankRef;

                if ("IMPS".equals(payment.transferMode)) {
                    payment.status = "DISBURSED";
                    lmsGateway.recordDisbursement(payment.drawdownId, payment.amount, bankRef);
                    log.info("IMPS disbursed payment={} ref={}", payment.id, bankRef);
                } else {
                    payment.status = "POLLING";
                    log.info(
                            "NEFT initiated payment={} ref={}, awaiting settlement",
                            payment.id,
                            bankRef);
                }

                event.status = "PROCESSED";
            } catch (Exception e) {
                event.status = "FAILED";
                payment.status = "FAILED";
                log.error("Disbursement failed payment={}", payment.id, e);
            }
        }
    }
}
