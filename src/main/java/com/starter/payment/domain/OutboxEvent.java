package com.starter.payment.domain;

import com.starter.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Objects;

/**
 * Outbox row written in the same transaction as the business change, then processed after commit by
 * a scheduled poller. Operational infrastructure — not business/audit data.
 */
@Entity
@Table(name = "outbox_event")
public class OutboxEvent extends BaseEntity {

    @Column(name = "aggregate_type", nullable = false)
    public String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    public Long aggregateId;

    @Column(name = "event_type", nullable = false)
    public String eventType;

    @Column(columnDefinition = "TEXT")
    public String payload;

    @Column(nullable = false)
    public String status = "PENDING";

    /** How many times processing has been attempted; drives dead-lettering after a max. */
    @Column(nullable = false)
    public int attempts = 0;

    /** Lease expiry while IN_PROGRESS; an expired lease means a stuck event to reclaim. */
    @Column(name = "locked_until")
    public Instant lockedUntil;

    protected OutboxEvent() {}

    public OutboxEvent(String aggregateType, Long aggregateId, String eventType, String payload) {
        this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType required");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId required");
        this.eventType = Objects.requireNonNull(eventType, "eventType required");
        this.payload = payload;
    }
}
