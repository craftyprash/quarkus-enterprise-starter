package com.starter.drawdown.internal;

import com.starter.applicant.ApplicantApi;
import com.starter.drawdown.DrawdownApi;
import com.starter.drawdown.domain.Drawdown;
import com.starter.payment.PaymentApi;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.NoSuchElementException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@ApplicationScoped
public class DrawdownService implements DrawdownApi {

    private static final Logger log = LoggerFactory.getLogger(DrawdownService.class);

    @Inject DrawdownRepo repo;
    @Inject ApplicantApi applicantApi;
    @Inject PaymentApi paymentApi;

    @Override
    @Transactional
    public Info create(CreateInput input) {
        var applicant = applicantApi.findById(input.applicantId());

        var drawdown = new Drawdown(applicant.id(), input.anchorCode(), input.amount());
        repo.persist(drawdown);

        log.info("Drawdown created id={} applicant={}", drawdown.id, applicant.id());
        return toInfo(drawdown, applicant.name());
    }

    @Override
    public Info findById(Long id) {
        var drawdown =
                repo.findByIdOptional(id)
                        .orElseThrow(() -> new NoSuchElementException("Drawdown not found"));
        var applicant = applicantApi.findById(drawdown.applicantId);
        return toInfo(drawdown, applicant.name());
    }

    @Override
    @Transactional
    public Info disburse(Long id) {
        var drawdown =
                repo.findByIdOptional(id)
                        .orElseThrow(() -> new NoSuchElementException("Drawdown not found"));

        if (!"PENDING".equals(drawdown.status)) {
            throw new IllegalStateException("Drawdown not in PENDING status");
        }

        drawdown.status = "DISBURSING";

        paymentApi.initiate(
                new PaymentApi.InitiateInput(drawdown.id, drawdown.anchorCode, drawdown.amount));

        var applicant = applicantApi.findById(drawdown.applicantId);
        log.info("Drawdown disbursement initiated id={}", drawdown.id);
        return toInfo(drawdown, applicant.name());
    }

    private Info toInfo(Drawdown d, String applicantName) {
        return new Info(
                d.id, d.applicantId, applicantName, d.anchorCode, d.amount, d.status, d.createdAt);
    }
}
