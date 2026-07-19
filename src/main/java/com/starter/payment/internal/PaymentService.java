package com.starter.payment.internal;

import com.starter.common.exception.NotFoundException;
import com.starter.payment.PaymentApi;
import com.starter.payment.domain.OutboxEvent;
import com.starter.payment.domain.Payment;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
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

    // Illustrative routing only — replace with the real transfer-mode policy. Not a business rule.
    private static final BigDecimal IMPS_LIMIT = new BigDecimal("200000.00");
    private static final String BANK = "MOCKBANK";

    @Inject PaymentRepo paymentRepo;
    @Inject OutboxRepo outboxRepo;

    /** Snapshot handed to the processor so the remote call runs with no open transaction. */
    public record DisburseTask(
            Long eventId,
            Long paymentId,
            Long drawdownId,
            String transferMode,
            BigDecimal amount) {}

    public record PollTask(
            Long paymentId, Long drawdownId, BigDecimal amount, String bankReference) {}

    // ── Public contract ────────────────────────────────────────────────

    @Override
    @Transactional
    public Info initiate(InitiateInput input) {
        // Idempotent: one payment per drawdown. A retried disburse returns the existing payment.
        var existing = paymentRepo.findByDrawdownId(input.drawdownId());
        if (existing.isPresent()) {
            return toInfo(existing.get());
        }

        var transferMode = input.amount().compareTo(IMPS_LIMIT) <= 0 ? "IMPS" : "NEFT";
        var payment = new Payment(input.drawdownId(), BANK, transferMode, input.amount());
        paymentRepo.persist(payment);

        var payload =
                "{\"paymentId\":%d,\"drawdownId\":%d}".formatted(payment.id, input.drawdownId());
        outboxRepo.persist(new OutboxEvent("PAYMENT", payment.id, "DISBURSE_REQUESTED", payload));

        log.info(
                "Payment initiated id={} mode={} drawdown={}",
                payment.id,
                transferMode,
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
    public List<Long> pendingDisbursementEventIds() {
        return outboxRepo.findPending("DISBURSE_REQUESTED").stream().map(e -> e.id).toList();
    }

    /**
     * Claim one event: mark it IN_PROGRESS and the payment PROCESSING. Returns empty if not
     * claimable.
     */
    @Transactional
    public Optional<DisburseTask> claim(Long eventId) {
        var event = outboxRepo.findById(eventId);
        if (event == null || !"PENDING".equals(event.status)) {
            return Optional.empty();
        }
        var payment = paymentRepo.findById(event.aggregateId);
        if (payment == null) {
            event.status = "SKIPPED";
            log.warn("Outbox event {} references missing payment {}", event.id, event.aggregateId);
            return Optional.empty();
        }
        event.status = "IN_PROGRESS";
        payment.status = "PROCESSING";
        return Optional.of(
                new DisburseTask(
                        event.id,
                        payment.id,
                        payment.drawdownId,
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
        var payment = paymentRepo.findById(paymentId);
        if (payment != null && "PROCESSING".equals(payment.status)) {
            payment.status = "FAILED";
        }
        markEvent(eventId, "FAILED");
    }

    @Transactional
    public List<PollTask> pollingTasks() {
        return paymentRepo.findByStatus("POLLING").stream()
                .map(p -> new PollTask(p.id, p.drawdownId, p.amount, p.bankReference))
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
