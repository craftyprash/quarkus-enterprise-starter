package com.starter.payment.internal;

import com.starter.common.integration.bank.BankRouter;
import com.starter.payment.PaymentApi;
import com.starter.payment.domain.OutboxEvent;
import com.starter.payment.domain.Payment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class PaymentService implements PaymentApi {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    @Inject PaymentRepo paymentRepo;
    @Inject OutboxRepo outboxRepo;
    @Inject BankRouter bankRouter;

    @Override
    @Transactional
    public Info initiate(InitiateInput input) {
        var gateway = bankRouter.resolveByAnchor(input.anchorCode());
        var transferMode = gateway.transferMode(input.anchorCode());

        var payment =
                new Payment(input.drawdownId(), gateway.bankCode(), transferMode, input.amount());
        paymentRepo.persist(payment);

        var event =
                new OutboxEvent(
                        "PAYMENT",
                        payment.id,
                        "DISBURSE_REQUESTED",
                        """
                        {"paymentId": %d, "drawdownId": %d, "amount": "%s"}
                        """
                                .formatted(payment.id, input.drawdownId(), input.amount()));
        outboxRepo.persist(event);

        log.info(
                "Payment initiated id={} bank={} mode={} drawdown={}",
                payment.id,
                payment.bank,
                payment.transferMode,
                input.drawdownId());

        return toInfo(payment);
    }

    @Override
    public Info findById(Long id) {
        var payment =
                paymentRepo
                        .findByIdOptional(id)
                        .orElseThrow(() -> new NoSuchElementException("Payment not found"));
        return toInfo(payment);
    }

    private Info toInfo(Payment p) {
        return new Info(
                p.id,
                p.drawdownId,
                p.bank,
                p.transferMode,
                p.amount,
                p.status,
                p.bankReference,
                p.createdAt);
    }
}
