package com.starter.drawdown.internal;

import com.starter.applicant.ApplicantApi;
import com.starter.common.exception.BusinessValidationException;
import com.starter.common.exception.NotFoundException;
import com.starter.drawdown.DrawdownApi;
import com.starter.drawdown.domain.Drawdown;
import com.starter.payment.PaymentApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class DrawdownService implements DrawdownApi {

    private static final Logger log = LoggerFactory.getLogger(DrawdownService.class);

    // Illustrative sanctioned cap — replace with the real per-applicant/anchor limit lookup.
    private static final BigDecimal MAX_DRAWDOWN = new BigDecimal("10000000.00");

    @Inject DrawdownRepo repo;
    @Inject ApplicantApi applicantApi; // other modules only via their Api interface
    @Inject PaymentApi paymentApi;

    @Override
    @Transactional
    public Info create(CreateInput input) {
        var applicant = applicantApi.findById(input.applicantId()); // 404s if missing

        // Business rule (well-formed but unprocessable) — 422, not a field-shape error.
        if (input.amount().compareTo(MAX_DRAWDOWN) > 0) {
            throw new BusinessValidationException("Drawdown amount exceeds the sanctioned limit");
        }

        var drawdown = new Drawdown(applicant.id(), input.anchorCode(), input.amount());
        repo.persist(drawdown);
        log.info("Drawdown created id={} applicant={}", drawdown.id, applicant.id());
        return toInfo(drawdown, applicant.name());
    }

    @Override
    public Info findById(Long id) {
        var drawdown =
                repo.findByIdOptional(id)
                        .orElseThrow(() -> new NotFoundException("Drawdown not found"));
        var applicant = applicantApi.findById(drawdown.applicantId);
        return toInfo(drawdown, applicant.name());
    }

    @Override
    @Transactional
    public Info disburse(Long id) {
        var drawdown =
                repo.findByIdOptional(id)
                        .orElseThrow(() -> new NotFoundException("Drawdown not found"));

        if (!"PENDING".equals(drawdown.status)) {
            throw new IllegalStateException("Drawdown is not in PENDING status");
        }
        drawdown.status = "DISBURSING";

        // Payment.initiate only writes rows (payment + outbox) in this transaction — no remote
        // call here. The bank/LMS calls happen later in DisbursementProcessor, after commit.
        paymentApi.initiate(
                new PaymentApi.InitiateInput(drawdown.id, drawdown.anchorCode, drawdown.amount));

        var applicant = applicantApi.findById(drawdown.applicantId);
        log.info("Drawdown disbursement requested id={}", drawdown.id);
        return toInfo(drawdown, applicant.name());
    }

    private Info toInfo(Drawdown d, String applicantName) {
        return new Info(
                d.id, d.applicantId, applicantName, d.anchorCode, d.amount, d.status, d.createdAt);
    }
}
