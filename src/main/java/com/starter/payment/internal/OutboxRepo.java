package com.starter.payment.internal;

import com.starter.payment.domain.OutboxEvent;
import io.quarkus.hibernate.orm.panache.PanacheRepository;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class OutboxRepo implements PanacheRepository<OutboxEvent> {

    /**
     * Events ready to process: fresh ({@code PENDING}) or stuck ({@code IN_PROGRESS} whose lease
     * has expired — the worker that claimed them died). This is what makes processing crash-safe.
     */
    public List<OutboxEvent> findClaimable(String eventType, Instant now) {
        return find(
                        "eventType = ?1 and (status = 'PENDING'"
                                + " or (status = 'IN_PROGRESS' and lockedUntil < ?2))",
                        eventType,
                        now)
                .list();
    }

    public Optional<OutboxEvent> findByAggregateId(Long aggregateId) {
        return find("aggregateId", aggregateId).firstResultOptional();
    }
}
