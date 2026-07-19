package com.starter.payment.internal;

import com.starter.common.exception.NotFoundException;
import com.starter.common.integration.BankRouter;
import com.starter.payment.PaymentApi;
import com.starter.payment.domain.OutboxEvent;
import com.starter.payment.domain.Payment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Payment writes + the transactional steps of the outbox flow. Each method here is a single, fast
 * DB transaction. Remote calls (bank, LMS) live in {@link DisbursementProcessor} / {@link
 * NeftSettlementPoller}, between these steps — never inside a transaction.
 */
@ApplicationScoped
public class PaymentService implements PaymentApi {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private static final int MAX_ATTEMPTS = 3; // then dead-letter
    private static final Duration LEASE = Duration.ofMinutes(1); // claim lease before reclaim

    @Inject PaymentRepo paymentRepo;
    @Inject OutboxRepo outboxRepo;
    @Inject BankRouter bankRouter;

    /** Snapshot handed to the processor so the remote call runs with no open transaction. */
    public record DisburseTask(
            Long eventId,
            Long paymentId,
            Long drawdownId,
            String bank,
            String transferMode,
            BigDecimal amount) {}

    public record PollTask(
            Long paymentId,
            Long drawdownId,
            String bank,
            BigDecimal amount,
            String bankReference) {}

    // ── Public contract ────────────────────────────────────────────────

    @Override
    @Transactional
    public Info initiate(InitiateInput input) {
        // Idempotent: one payment per drawdown. A retried disburse returns the existing payment.
        var existing = paymentRepo.findByDrawdownId(input.drawdownId());
        if (existing.isPresent()) {
            return toInfo(existing.get());
        }

        // Route by anchor to a bank + transfer rail (BankRouter discovers gateways via CDI).
        var gateway = bankRouter.resolveByAnchor(input.anchorCode());
        var payment =
                new Payment(
                        input.drawdownId(),
                        gateway.bankCode(),
                        gateway.transferMode(input.anchorCode()),
                        input.amount());
        paymentRepo.persist(payment);

        var payload =
                "{\"paymentId\":%d,\"drawdownId\":%d}".formatted(payment.id, input.drawdownId());
        outboxRepo.persist(new OutboxEvent("PAYMENT", payment.id, "DISBURSE_REQUESTED", payload));

        log.info(
                "Payment initiated id={} bank={} mode={} drawdown={}",
                payment.id,
                payment.bank,
                payment.transferMode,
                payment.drawdownId);
        return toInfo(payment);
    }

    @Override
    public Info findById(Long id) {
        return toInfo(
                paymentRepo
                        .findByIdOptional(id)
                        .orElseThrow(() -> new NotFoundException("Payment not found")));
    }

    // ── Outbox transactional steps (called by the processors) ──────────

    @Transactional
    public List<Long> claimableDisbursementEventIds() {
        return outboxRepo.findClaimable("DISBURSE_REQUESTED", Instant.now()).stream()
                .map(e -> e.id)
                .toList();
    }

    /**
     * Claim one event (fresh, or stuck with an expired lease): mark it IN_PROGRESS with a new
     * lease, bump the attempt count, and the payment PROCESSING. Empty if not claimable.
     */
    @Transactional
    public Optional<DisburseTask> claim(Long eventId) {
        var event = outboxRepo.findById(eventId);
        if (event == null || !isClaimable(event)) {
            return Optional.empty();
        }
        var payment = paymentRepo.findById(event.aggregateId);
        if (payment == null) {
            event.status = "SKIPPED";
            log.warn("Outbox event {} references missing payment {}", event.id, event.aggregateId);
            return Optional.empty();
        }
        event.status = "IN_PROGRESS";
        event.attempts += 1;
        event.lockedUntil = Instant.now().plus(LEASE);
        payment.status = "PROCESSING";
        return Optional.of(
                new DisburseTask(
                        event.id,
                        payment.id,
                        payment.drawdownId,
                        payment.bank,
                        payment.transferMode,
                        payment.amount));
    }

    @Transactional
    public void completeImmediate(Long eventId, Long paymentId, String bankReference) {
        var payment = requireProcessing(paymentId);
        payment.bankReference = bankReference;
        payment.status = "DISBURSED";
        markEvent(eventId, "PROCESSED");
    }

    @Transactional
    public void markAwaitingSettlement(Long eventId, Long paymentId, String bankReference) {
        var payment = requireProcessing(paymentId);
        payment.bankReference = bankReference;
        payment.status = "POLLING";
        markEvent(eventId, "PROCESSED");
    }

    @Transactional
    public void failDisbursement(Long eventId, Long paymentId) {
        var event = outboxRepo.findById(eventId);
        boolean dead = event != null && event.attempts >= MAX_ATTEMPTS;
        if (event != null) {
            event.status = dead ? "DEAD" : "PENDING"; // dead-letter, or retry on the next tick
            event.lockedUntil = null;
        }
        if (dead) {
            var payment = paymentRepo.findById(paymentId);
            if (payment != null) {
                payment.status = "FAILED";
                log.error("Payment dead-lettered after {} attempts id={}", MAX_ATTEMPTS, paymentId);
            }
        }
    }

    @Transactional
    public List<PollTask> pollingTasks() {
        return paymentRepo.findByStatus("POLLING").stream()
                .map(p -> new PollTask(p.id, p.drawdownId, p.bank, p.amount, p.bankReference))
                .toList();
    }

    @Transactional
    public void markSettled(Long paymentId) {
        var payment = paymentRepo.findById(paymentId);
        if (payment != null && "POLLING".equals(payment.status)) { // idempotent guard
            payment.status = "DISBURSED";
            log.info("Payment settled id={} ref={}", payment.id, payment.bankReference);
        }
    }

    @Transactional
    public void failSettlement(Long paymentId) {
        var payment = paymentRepo.findById(paymentId);
        if (payment != null && "POLLING".equals(payment.status)) {
            payment.status = "FAILED";
        }
    }

    private static boolean isClaimable(OutboxEvent event) {
        return "PENDING".equals(event.status)
                || ("IN_PROGRESS".equals(event.status)
                        && event.lockedUntil != null
                        && event.lockedUntil.isBefore(Instant.now()));
    }

    private Payment requireProcessing(Long paymentId) {
        var payment = paymentRepo.findById(paymentId);
        if (payment == null || !"PROCESSING".equals(payment.status)) {
            throw new IllegalStateException("Payment not in PROCESSING state: " + paymentId);
        }
        return payment;
    }

    private void markEvent(Long eventId, String status) {
        var event = outboxRepo.findById(eventId);
        if (event != null) {
            event.status = status;
        }
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
