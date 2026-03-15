package com.starter.payment.domain;

import com.starter.common.domain.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.Objects;

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

    protected OutboxEvent() {}

    public OutboxEvent(String aggregateType, Long aggregateId, String eventType, String payload) {
        this.aggregateType = Objects.requireNonNull(aggregateType, "aggregateType required");
        this.aggregateId = Objects.requireNonNull(aggregateId, "aggregateId required");
        this.eventType = Objects.requireNonNull(eventType, "eventType required");
        this.payload = payload;
    }
}
